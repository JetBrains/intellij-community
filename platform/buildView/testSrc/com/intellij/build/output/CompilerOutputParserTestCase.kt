// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartId
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertInstanceOf
import java.nio.file.Path

@TestApplication
abstract class CompilerOutputParserTestCase {

  protected val testRoot by tempPathFixture()

  protected abstract fun createParser(): BuildOutputParser

  protected fun parseEvents(output: String): List<BuildEvent> {
    val events = mutableListOf<BuildEvent>()
    val listener = BuildProgressListener { _, event -> events += event }
    BuildOutputInstantReaderImpl(StartId(), StartId(), listener, listOf(createParser()))
      .use { it.append(output) }
    return events
  }

  protected fun parseSingleMessageEvent(output: String): MessageEvent {
    val events = parseEvents(output)
    assertEquals(1, events.size) {
      "Actual: $events"
    }
    val event = events.single()
    assertInstanceOf<MessageEvent>(event)
    return event
  }

  protected fun parseSingleFileMessageEvent(output: String): FileMessageEvent {
    val event = parseSingleMessageEvent(output)
    assertInstanceOf<FileMessageEvent>(event)
    return event
  }

  protected fun assertMessageEvent(event: MessageEvent, kind: MessageEvent.Kind, message: String, description: String) {
    assertEquals(kind, event.kind)
    assertEquals(message, event.message)
    assertEquals(description.trimEnd(), event.description?.trimEnd())
    assertEquals(description.trimEnd(), event.result.details?.trimEnd())
  }

  protected fun assertFilePosition(event: FileMessageEvent, path: Path, line: Int, column: Int) {
    assertEquals(path, event.filePosition.path)
    assertEquals(line, event.filePosition.startLine)
    assertEquals(column, event.filePosition.startColumn)
  }
}
