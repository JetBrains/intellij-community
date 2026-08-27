// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GroovycOutputParserTest {

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `test groovyc error navigates to line and column`() {
    val path = Files.createFile(tempDir.resolve("JlinkTask.groovy"))
    val events = parseEvents(
      """
      > Task :compileGroovy FAILED
      startup failed:
      $path: 96: unable to resolve class Provider <? extends java.lang.Iterable <String>>
       @ line 96, column 28.

      """.trimIndent()
    )

    assertEquals(1, events.size)
    val event = events.single()
    assertTrue(event is FileMessageEvent, "Expected FileMessageEvent, got ${event.javaClass.name}")
    event as FileMessageEvent

    assertEquals(MessageEvent.Kind.ERROR, event.kind)
    assertEquals("unable to resolve class Provider <? extends java.lang.Iterable <String>>", event.message)
    assertEquals(path, event.filePosition.path)
    assertEquals(95, event.filePosition.startLine)
    assertEquals(27, event.filePosition.startColumn)
  }

  @Test
  fun `test groovyc error with location on the same line`() {
    val path = Files.createFile(tempDir.resolve("Script.groovy"))
    val events = parseEvents(
      """
      > Task :compileGroovy FAILED
      startup failed:
      $path: 7: unexpected token: static @ line 7, column 1.

      """.trimIndent()
    )

    assertEquals(1, events.size)
    val event = events.single()
    assertTrue(event is FileMessageEvent, "Expected FileMessageEvent, got ${event.javaClass.name}")
    event as FileMessageEvent

    assertEquals(MessageEvent.Kind.ERROR, event.kind)
    assertEquals("unexpected token: static", event.message)
    assertEquals(path, event.filePosition.path)
    assertEquals(6, event.filePosition.startLine)
    assertEquals(0, event.filePosition.startColumn)
  }

  private fun parseEvents(output: String): List<BuildEvent> {
    val events = mutableListOf<BuildEvent>()
    val buildId = Any()
    BuildOutputInstantReaderImpl(
      buildId, buildId,
      BuildProgressListener { _, event -> events += event },
      listOf(GroovycOutputParser())
    ).append(output).close()
    return events
  }
}