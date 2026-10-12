// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildId
import com.intellij.build.events.OutputId
import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputMulticaster.Companion.asMulticaster
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEqualsOrdered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertSingle
import com.intellij.testFramework.common.timeoutRunBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

internal class BuildOutputLineParserDispatcherTest {

  private companion object {
    val NOOP_MULTICASTER = object : BuildOutputMulticaster {
      override fun notifyBuildEvent(event: BuildEvent) = Unit
    }
  }

  @Test
  fun `test lines are parsed`() = timeoutRunBlocking {
    val parser = RecordingBuildOutputParser()

    val taskId = StartId()
    val outputId1 = OutputId()
    val outputId2 = OutputId()

    val dispatcher = BuildOutputParserDispatcherImpl(taskId, listOf(parser), NOOP_MULTICASTER)
    dispatcher.notifyTextAvailable(outputId1, "a\n")
    dispatcher.notifyTextAvailable(outputId2, "b\n")
    dispatcher.close()

    parser.assertLines("a" to listOf(outputId1), "b" to listOf(outputId2))
  }

  @Test
  fun `test blank lines are skipped`() = timeoutRunBlocking {
    val parsedLines = CopyOnWriteArrayList<BuildOutputText>()
    val lookaheadLines = CopyOnWriteArrayList<BuildOutputText>()
    val parser = object : BuildOutputLineParser {
      override suspend fun parse(
        line: BuildOutputText,
        reader: BuildOutputReplayableLineReader,
        taskId: StartId,
        multicaster: BuildOutputMulticaster,
      ): Boolean {
        if (parsedLines.isEmpty()) {
          lookaheadLines.add(line)
          var nextLine = reader.readLine()
          while (nextLine != null) {
            lookaheadLines.add(nextLine)
            nextLine = reader.readLine()
          }
        }
        parsedLines += line
        return false
      }
    }

    val taskId = StartId()
    val outputId1 = OutputId()
    val outputId2 = OutputId()
    val outputId3 = OutputId()

    val dispatcher = BuildOutputParserDispatcherImpl(taskId, listOf(parser), NOOP_MULTICASTER)
    dispatcher.notifyTextAvailable(outputId1, "a\n")
    dispatcher.notifyTextAvailable(outputId2, "\n")
    dispatcher.notifyTextAvailable(outputId3, "b\n")
    dispatcher.close()

    assertEqualsOrdered(
      listOf("a" to listOf(outputId1), "b" to listOf(outputId3)),
      parsedLines.map { it.text to it.ids },
    )
    assertEqualsOrdered(
      listOf("a" to listOf(outputId1), "" to listOf(outputId2), "b" to listOf(outputId3)),
      lookaheadLines.map { it.text to it.ids },
    )
  }

  @Test
  fun `test lines are parsed by multiple parsers`() = timeoutRunBlocking {
    val firstParser = RecordingBuildOutputParser(result = false)
    val secondParser = RecordingBuildOutputParser(result = true)

    val taskId = StartId()
    val outputId1 = OutputId()
    val outputId2 = OutputId()

    val dispatcher = BuildOutputParserDispatcherImpl(taskId, listOf(firstParser, secondParser), NOOP_MULTICASTER)
    dispatcher.notifyTextAvailable(outputId1, "a\n")
    dispatcher.notifyTextAvailable(outputId2, "b\n")
    dispatcher.close()

    firstParser.assertLines("a" to listOf(outputId1), "b" to listOf(outputId2))
    secondParser.assertLines("a" to listOf(outputId1), "b" to listOf(outputId2))
  }

  @Test
  fun `test parser returning true stops further parsers`() = timeoutRunBlocking {
    val firstParser = RecordingBuildOutputParser(result = true)
    val secondParser = RecordingBuildOutputParser(result = true)

    val taskId = StartId()
    val outputId = OutputId()

    val dispatcher = BuildOutputParserDispatcherImpl(taskId, listOf(firstParser, secondParser), NOOP_MULTICASTER)
    dispatcher.notifyTextAvailable(outputId, "a\n")
    dispatcher.close()

    firstParser.assertLines("a" to listOf(outputId))
    secondParser.assertNoLines()
  }

  @Test
  fun `test pushBack inside parser works correctly`() = timeoutRunBlocking {
    val lines = CopyOnWriteArrayList<Pair<String, List<Pair<String, List<OutputId>>>>>()

    val parser = object : BuildOutputLineParser {
      override suspend fun parse(
        line: BuildOutputText,
        reader: BuildOutputReplayableLineReader,
        taskId: StartId,
        multicaster: BuildOutputMulticaster,
      ): Boolean {
        val parserLines = mutableListOf<Pair<String, List<OutputId>>>()
        repeat(2) {
          val nextLine = reader.readLine()
          if (nextLine != null) {
            parserLines += nextLine.text to nextLine.ids
            reader.pushBack(1)
          }
        }
        lines.add(line.text to parserLines)
        return false
      }
    }

    val taskId = StartId()
    val outputId1 = OutputId()
    val outputId2 = OutputId()
    val outputId3 = OutputId()

    val dispatcher = BuildOutputParserDispatcherImpl(taskId, listOf(parser), NOOP_MULTICASTER)
    dispatcher.notifyTextAvailable(outputId1, "a\n")
    dispatcher.notifyTextAvailable(outputId2, "b\n")
    dispatcher.notifyTextAvailable(outputId3, "c\n")
    dispatcher.close()

    assertEquals(
      listOf(
        "a" to listOf("b" to listOf(outputId2), "b" to listOf(outputId2)),
        "b" to listOf("c" to listOf(outputId3), "c" to listOf(outputId3)),
        "c" to emptyList(),
      ),
      lines,
    )
  }

  @Test
  fun `test dispatcher close waits for reader to finish`() = timeoutRunBlocking {
    val parser = RecordingBuildOutputParser { Thread.sleep(50); true }

    val taskId = StartId()
    val outputId1 = OutputId()
    val outputId2 = OutputId()

    val dispatcher = BuildOutputParserDispatcherImpl(taskId, listOf(parser), NOOP_MULTICASTER)
    dispatcher.notifyTextAvailable(outputId1, "a\n")
    dispatcher.notifyTextAvailable(outputId2, "b\n")
    dispatcher.close()

    parser.assertLines("a" to listOf(outputId1), "b" to listOf(outputId2))
  }

  @Test
  fun `test partial lines are merged with all output ids`() = timeoutRunBlocking {
    val parser = RecordingBuildOutputParser()

    val taskId = StartId()
    val outputId1 = OutputId()
    val outputId2 = OutputId()

    val dispatcher = BuildOutputParserDispatcherImpl(taskId, listOf(parser), NOOP_MULTICASTER)
    dispatcher.notifyTextAvailable(outputId1, "ab")
    dispatcher.notifyTextAvailable(outputId2, "cd\n")
    dispatcher.close()

    parser.assertLines("abcd" to listOf(outputId1, outputId2))
  }

  @Test
  fun `test parser receives the dispatcher taskId`() = timeoutRunBlocking {
    val seenTaskIds = CopyOnWriteArrayList<StartId>()
    val parser = object : BuildOutputLineParser {
      override suspend fun parse(
        line: BuildOutputText,
        reader: BuildOutputReplayableLineReader,
        taskId: StartId,
        multicaster: BuildOutputMulticaster,
      ): Boolean {
        seenTaskIds += taskId
        return true
      }
    }

    val taskId = StartId()

    val dispatcher = BuildOutputParserDispatcherImpl(taskId, listOf(parser), NOOP_MULTICASTER)
    dispatcher.notifyTextAvailable(OutputId(), "a\n")
    dispatcher.notifyTextAvailable(OutputId(), "b\n")
    dispatcher.close()

    assertEquals(listOf(taskId, taskId), seenTaskIds)
  }

  @Test
  fun `duplicate events are not forwarded`() = timeoutRunBlocking {
    val seenEvents = CopyOnWriteArrayList<BuildEvent>()
    val listener = BuildProgressListener { _, event -> seenEvents += event }

    val event = object : BuildEvent {
      override fun getId(): Any = Any()
      override fun getParentId(): Any? = null
      override fun getEventTime(): Long = 0
      override fun getMessage(): String = ""
      override fun getHint(): String? = null
      override fun getDescription(): String? = null
    }

    val parser = object : BuildOutputLineParser {
      override suspend fun parse(
        line: BuildOutputText,
        reader: BuildOutputReplayableLineReader,
        taskId: StartId,
        multicaster: BuildOutputMulticaster,
      ): Boolean {
        multicaster.notifyBuildEvent(event)
        multicaster.notifyBuildEvent(event)
        multicaster.notifyBuildEvent(event)
        multicaster.notifyBuildEvent(event)
        multicaster.notifyBuildEvent(event)
        return true
      }
    }

    val buildId = BuildId()

    val dispatcher = BuildOutputParserDispatcherImpl(StartId(), listOf(parser), listener.asMulticaster(buildId))
    dispatcher.notifyTextAvailable(OutputId(), "trigger\n")
    dispatcher.close()

    assertSingle(event, seenEvents)
  }

  private suspend fun BuildOutputParserDispatcher.notifyTextAvailable(outputId: OutputId, text: String) {
    notifyTextAvailable(BuildOutputText.createOutputText(outputId, text))
  }

  private class RecordingBuildOutputParser(
    private val parse: () -> Boolean,
  ) : BuildOutputLineParser {

    constructor(result: Boolean = true)
      : this({ result })

    private val lines: MutableList<List<Pair<String, List<OutputId>>>> = CopyOnWriteArrayList()

    fun assertNoLines() {
      assertEmpty(lines)
    }

    fun assertLines(vararg expected: Pair<String, List<OutputId>>) {
      assertSingle(expected.asList(), lines)
    }

    override suspend fun parse(
      line: BuildOutputText,
      reader: BuildOutputReplayableLineReader,
      taskId: StartId,
      multicaster: BuildOutputMulticaster,
    ): Boolean {
      val allLines = mutableListOf(line)
      var line = reader.readLine()
      while (line != null) {
        allLines.add(line)
        line = reader.readLine()
      }
      lines.add(allLines.map { it.text to it.ids })
      return parse()
    }
  }
}
