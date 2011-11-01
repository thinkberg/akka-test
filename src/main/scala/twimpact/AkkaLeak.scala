/*
 * Copyright (c) 2011 TWIMPACT UG (haftungsbeschraenkt). All rights reserved.
 */

package twimpact

import grizzled.slf4j.Logging
import akka.dispatch.{BoundedMailbox, Dispatchers}
import akka.actor.{ActorRef, Actor}
import akka.routing.{SmallestMailboxFirstIterator, Routing}
import java.util.Scanner

object AkkaLeak extends App with Logging {

  val dispatcher = Dispatchers.newExecutorBasedEventDrivenWorkStealingDispatcher(
    "akka-test-dispatcher", 1, mailboxType = new BoundedMailbox(capacity = 100)).build


  class Something(val s: Array[String])

  class SomethingElse(val s: Array[String], val n: Int)

  class AkkaTestActor extends Actor {
    self.id = "akka-test-actor"
    self.dispatcher = dispatcher

    protected def receive = {
      case (a: Something, b: SomethingElse) =>
        Thread.sleep(10)
    }
  }

  //**EXTRACT**********************************************************************************************

  lazy val actors = (1 to 100).foldRight(List[ActorRef]()) {
    (n, list) => Actor.actorOf(new AkkaTestActor).start() :: list
  }

  lazy val router = Routing.loadBalancerActor(new SmallestMailboxFirstIterator(actors))

  val scanner = new Scanner(System.in)
  println("Press enter to start (attach profiler)...")
  scanner.nextLine()

  private def backlogs = actors.map(_.mailboxSize).filter(_ > 0)

  for (i <- 1 until 100000) {
    val a = new Something(new Array[String](1000))
    val b = new SomethingElse(new Array[String](1000), i)
    router ! ((a, b))
    if (i % 1000 == 0) {
      val backlogs = actors.map(_.mailboxSize).filter(_ > 0)
      if (backlogs.length > 0)
        info("actor backlog: %d actors (%d waiting messages)".format(backlogs.length, backlogs.foldLeft(0)(_ + _)))
    }
  }

  while(backlogs.length > 0) {
    info("actor backlog: %d actors (%d waiting messages)".format(backlogs.length, backlogs.foldLeft(0)(_ + _)))
    Thread.sleep(1000)
  }
  println("Press enter to finish (detach profiler)...")
  scanner.nextLine()
  System.exit(0)
}
