// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.BuildEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.function.Consumer

class BuildOutputParserDispatcherTest {

  @Test
  fun `test lines are parsed`() = timeoutRunBlocking {
    val parser = RecordingBuildOutputParser()

    val dispatcher = BuildOutputParserDispatcherImpl(Any(), listOf(parser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.notifyTextAvailable("b\n")
    dispatcher.close()

    parser.assertLines("a", "b")
  }

  @Test
  fun `test blank lines are skipped`() = timeoutRunBlocking {
    val parser = RecordingBuildOutputParser()

    val dispatcher = BuildOutputParserDispatcherImpl(Any(), listOf(parser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.notifyTextAvailable("\n")
    dispatcher.notifyTextAvailable("b\n")
    dispatcher.close()

    parser.assertLines("a", "b")
  }

  @Test
  fun `test lines are parsed by multiple parsers`() = timeoutRunBlocking {
    val firstParser = RecordingBuildOutputParser(result = false)
    val secondParser = RecordingBuildOutputParser(result = false)

    val dispatcher = BuildOutputParserDispatcherImpl(Any(), listOf(firstParser, secondParser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.close()

    firstParser.assertLines("a")
    secondParser.assertLines("a")
  }

  @Test
  fun `test parser returning true stops further parsers`() = timeoutRunBlocking {
    val firstParser = RecordingBuildOutputParser(result = true)
    val secondParser = RecordingBuildOutputParser(result = true)

    val dispatcher = BuildOutputParserDispatcherImpl(Any(), listOf(firstParser, secondParser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.close()

    firstParser.assertLines("a")
    secondParser.assertLines()
  }

  @Test
  fun `test pushBack inside parser works correctly`() = timeoutRunBlocking {
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

    val dispatcher = BuildOutputParserDispatcherImpl(Any(), listOf(parser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.notifyTextAvailable("b\n")
    dispatcher.notifyTextAvailable("c\n")
    dispatcher.close()

    assertEquals(listOf("a" to listOf("b", "b"), "b" to listOf("c", "c"), "c" to emptyList()), lines)
  }

  @Test
  fun `test dispatcher close waits for reader to finish`() = timeoutRunBlocking {
    val parser = RecordingBuildOutputParser { Thread.sleep(50); true }

    val dispatcher = BuildOutputParserDispatcherImpl(Any(), listOf(parser), NoopBuildOutputMulticaster)
    dispatcher.notifyTextAvailable("a\n")
    dispatcher.notifyTextAvailable("b\n")
    dispatcher.close()

    parser.assertLines("a", "b")
  }

  @Test
  fun `duplicate events are not forwarded`() = timeoutRunBlocking {
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

    val dispatcher = BuildOutputParserDispatcherImpl(Any(), listOf(parser), multicaster)
    dispatcher.notifyTextAvailable("trigger\n")
    dispatcher.close()

    multicaster.assertEvents(event)
  }

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
