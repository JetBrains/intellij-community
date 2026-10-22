// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.StartId
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.time.Duration.Companion.seconds

class BuildOutputParserDispatcherTest {

  @Test
  fun `test lines are parsed`(): Unit = timeoutRunBlocking {
    val parser = RecordingBuildOutputParser()

    val dispatcher = BuildOutputParserDispatcherImpl(StartId(), listOf(parser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.notifyTextAvailable("b\n")
    dispatcher.close()

    parser.assertLines("a", "b")
  }

  @Test
  fun `test blank lines are skipped`(): Unit = timeoutRunBlocking {
    val parser = RecordingBuildOutputParser()

    val dispatcher = BuildOutputParserDispatcherImpl(StartId(), listOf(parser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.notifyTextAvailable("\n")
    dispatcher.notifyTextAvailable("b\n")
    dispatcher.close()

    parser.assertLines("a", "b")
  }

  @Test
  fun `test lines are parsed by multiple parsers`(): Unit = timeoutRunBlocking {
    val firstParser = RecordingBuildOutputParser(result = false)
    val secondParser = RecordingBuildOutputParser(result = false)

    val dispatcher = BuildOutputParserDispatcherImpl(StartId(), listOf(firstParser, secondParser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.close()

    firstParser.assertLines("a")
    secondParser.assertLines("a")
  }

  @Test
  fun `test parser returning true stops further parsers`(): Unit = timeoutRunBlocking {
    val firstParser = RecordingBuildOutputParser(result = true)
    val secondParser = RecordingBuildOutputParser(result = true)

    val dispatcher = BuildOutputParserDispatcherImpl(StartId(), listOf(firstParser, secondParser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.close()

    firstParser.assertLines("a")
    secondParser.assertLines()
  }

  @Test
  fun `test pushBack inside parser works correctly`(): Unit = timeoutRunBlocking {
    val lines = mutableListOf<Pair<String, List<String>>>()

    val parser = BuildOutputParser { line, reader, _ ->
      val parserLines = mutableListOf<String>()
      repeat(2) {
        val nextLine = reader.readLine()
        if (nextLine != null) {
          parserLines += nextLine
          reader.pushBack()
        }
      }
      lines.add(line to parserLines)
      false
    }

    val dispatcher = BuildOutputParserDispatcherImpl(StartId(), listOf(parser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.notifyTextAvailable("b\n")
    dispatcher.notifyTextAvailable("c\n")
    dispatcher.close()

    assertEquals(listOf("a" to listOf("b", "b"), "b" to listOf("c", "c"), "c" to emptyList()), lines)
  }

  @Test
  fun `test dispatcher close waits for reader to finish`(): Unit = timeoutRunBlocking {
    val parser = RecordingBuildOutputParser { Thread.sleep(50); true }

    val dispatcher = BuildOutputParserDispatcherImpl(StartId(), listOf(parser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.notifyTextAvailable("b\n")
    dispatcher.close()

    parser.assertLines("a", "b")
  }

  @Test
  fun `duplicate events are not forwarded`(): Unit = timeoutRunBlocking {
    val multicaster = RecordingBuildOutputMulticaster()

    val event = object : BuildEvent {
      override fun getId(): Any = Any()
      override fun getParentId(): Any? = null
      override fun getEventTime(): Long = 0
      override fun getMessage(): String = ""
      override fun getHint(): String? = null
      override fun getDescription(): String? = null
    }

    val parser = BuildOutputParser { _, _, messageConsumer ->
      messageConsumer.accept(event)
      messageConsumer.accept(event)
      messageConsumer.accept(event)
      messageConsumer.accept(event)
      messageConsumer.accept(event)
      true
    }

    val dispatcher = BuildOutputParserDispatcherImpl(StartId(), listOf(parser), multicaster)
    dispatcher.notifyTextAvailable("trigger\n")
    dispatcher.close()

    multicaster.assertEvents(event)
  }

  /**
   * The producer of the build output blocks a process output reader thread, and that thread drains a
   * pipe the producing process writes to. The parser coroutine must therefore never wait for a shared
   * [Dispatchers.IO] permit, or the whole chain deadlocks. See IDEA-395143.
   */
  @Test
  fun `test lines are parsed while Dispatchers IO is exhausted`(): Unit = timeoutRunBlocking(30.seconds) {
    val parser = RecordingBuildOutputParser()
    val permits = ioParallelism()
    val occupied = CountDownLatch(permits)
    val release = CountDownLatch(1)
    try {
      repeat(permits) {
        launch(Dispatchers.IO) {
          occupied.countDown()
          release.await() // blocks the thread on purpose, so the permit is not released
        }
      }
      // Wait for the hogs to take every permit. The wait is bounded on purpose: another permit holder
      // in the test JVM keeps some hogs queued, yet it exhausts the pool just as well, so a partial
      // countdown is still a valid precondition. The test thread is not a Dispatchers.IO worker, so
      // blocking it here is safe.
      occupied.await(10, TimeUnit.SECONDS)

      val dispatcher = BuildOutputParserDispatcherImpl(StartId(), listOf(parser), NoopBuildOutputMulticaster)
      dispatcher.notifyTextAvailable("a\n")
      dispatcher.close()
    }
    finally {
      release.countDown()
    }

    parser.assertLines("a")
  }

  /** The number of permits of [Dispatchers.IO], as `DefaultIoScheduler` computes it. */
  private fun ioParallelism(): Int =
    System.getProperty("kotlinx.coroutines.io.parallelism")?.toIntOrNull()
    ?: 64.coerceAtLeast(Runtime.getRuntime().availableProcessors())

  private object NoopBuildOutputMulticaster : BuildOutputMulticaster {
    override fun notifyBuildEvent(event: BuildEvent): Unit = Unit
  }

  private class RecordingBuildOutputParser(
    private val parse: () -> Boolean,
  ) : BuildOutputParser {

    constructor(result: Boolean = true) : this({ result })

    private val lines = mutableListOf<String>()

    fun assertLines(vararg expected: String) {
      assertEquals(expected.asList(), lines)
    }

    override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
      lines.add(line)
      return parse()
    }
  }

  private class RecordingBuildOutputMulticaster : BuildOutputMulticaster {

    private val events = mutableListOf<BuildEvent>()

    fun assertEvents(vararg expected: BuildEvent) {
      assertEquals(expected.asList(), events)
    }

    override fun notifyBuildEvent(event: BuildEvent) {
      events += event
    }
  }
}
