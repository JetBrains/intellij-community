// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.build.events.OutputId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildOutputLineProcessorTest {

  @Test
  suspend fun `buffer is not flushed after a normal line`() {
    val processor = BuildOutputLineProcessor { _ -> throw IllegalStateException("Buffer should not be flushed") }
    repeat(5) {
      processor.notifyTextAvailable(BuildOutputText.createOutputText("hello"))
    }
  }

  @Test
  suspend fun `buffer is flushed after a line with line break`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    repeat(5) {
      processor.notifyTextAvailable(BuildOutputText.createOutputText("hello"))
    }
    assertTrue(collectedLines.isEmpty())
    processor.notifyTextAvailable(BuildOutputText.createOutputText("\n"))
    assertEquals(1, collectedLines.size)
    assertEquals("hellohellohellohellohello\n", collectedLines.first())
  }

  @Test
  suspend fun `resulting line contains all output ids`() {
    val collectedIds = mutableListOf<OutputId>()
    val processor = BuildOutputLineProcessor { line ->
      collectedIds.add(line.ids)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText(1, "a"))
    processor.notifyTextAvailable(BuildOutputText.createOutputText(2, "b"))
    processor.notifyTextAvailable(BuildOutputText.createOutputText(3, "c"))
    processor.notifyTextAvailable(BuildOutputText.createOutputText("\n"))
    assertEquals(1, collectedIds.size)
    assertEquals(listOf(1, 2, 3), collectedIds.first())
  }

  @Test
  suspend fun `line with multiple line breaks flushed on each line break`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("hello\nhello\nhello\n"))
    assertEquals(listOf("hello\n", "hello\n", "hello\n"), collectedLines)
  }

  @Test
  suspend fun `assert same output id for a line with line breaks`() {
    val collectedIds = mutableListOf<OutputId>()
    val processor = BuildOutputLineProcessor { line ->
      collectedIds.add(line.ids)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText(1, "hello\nhello\nhello\n"))
    assertEquals(
      listOf(listOf(1), listOf(1), listOf(1)),
      collectedIds
    )
  }

  @Test
  suspend fun `assert first carriage return is dropped`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("\rhello\rworld\n"))
    assertEquals("world\n", collectedLines.first())
  }

  @Test
  suspend fun `assert carriage return is handled as a line break`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("hello\rworld\rthis\ris\rsomething\rspecial\r"))
    processor.notifyTextAvailable(BuildOutputText.createOutputText("\rsomething\n"))
    assertEquals(listOf("something\n"), collectedLines)
  }

  @Test
  suspend fun `carriage return with a linebreak handled as a single line break`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("hello\r\nworld\r\n"))
    assertEquals(listOf("hello\n", "world\n"), collectedLines)
  }

  @Test
  suspend fun `carriage return is handled across chunks`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("hello\r"))
    processor.notifyTextAvailable(BuildOutputText.createOutputText("\nworld\n"))
    assertEquals(listOf("hello\n", "world\n"), collectedLines)
  }

  @Test
  suspend fun `carriage return at chunk end resets the line before the next text`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("hello\r"))
    processor.notifyTextAvailable(BuildOutputText.createOutputText("world\n"))
    assertEquals(listOf("world\n"), collectedLines)
  }

  @Test
  suspend fun `carriage return resets the line before carriage return`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("progress 1\rprogress 2\r\n"))
    assertEquals(listOf("progress 2\n"), collectedLines)
  }

  @Test
  suspend fun `multiple carriage returns before line feed keep the line`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("hello\r\r\n"))
    assertEquals(listOf("hello\n"), collectedLines)
  }

  @Test
  suspend fun `multiple carriage returns before line keep the line across chunks`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("hello\r"))
    processor.notifyTextAvailable(BuildOutputText.createOutputText("\r"))
    processor.notifyTextAvailable(BuildOutputText.createOutputText("\r\n"))
    assertEquals(listOf("hello\n"), collectedLines)
  }

  @Test
  suspend fun `carriage return does not affect the next line`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("a\rb\nc\n"))
    assertEquals(listOf("b\n", "c\n"), collectedLines)
  }

  @Test
  suspend fun `pending carriage return keeps the line on close`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("hello\r"))
    processor.close()
    assertEquals(listOf("hello\n"), collectedLines)
  }

  @Test
  suspend fun `text after carriage return is flushed on close`() {
    val collectedLines = mutableListOf<String>()
    val processor = BuildOutputLineProcessor { line ->
      collectedLines.add(line.text)
    }
    processor.notifyTextAvailable(BuildOutputText.createOutputText("hello\rworld"))
    processor.close()
    assertEquals(listOf("world\n"), collectedLines)
  }
}
