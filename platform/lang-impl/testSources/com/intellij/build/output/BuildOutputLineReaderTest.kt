// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.milliseconds

class BuildOutputLineReaderTest {

  @Test
  fun `single line from single notify`() = timeoutRunBlocking {
    val reader = BuildOutputLineReaderImpl()

    reader.notifyTextAvailable("hello\n")

    assertEquals("hello", reader.readLine())

    reader.close()

    assertNull(reader.readLine())
  }

  @Test
  fun `single line from multiple notify`() = timeoutRunBlocking {
    val reader = BuildOutputLineReaderImpl()

    reader.notifyTextAvailable("he")
    reader.notifyTextAvailable("llo\n")

    assertEquals("hello", reader.readLine())

    reader.close()

    assertNull(reader.readLine())
  }

  @Test
  fun `multiple lines from single notify`() = timeoutRunBlocking {

    val reader = BuildOutputLineReaderImpl()

    reader.notifyTextAvailable("a\nb\nc\n")

    assertEquals("a", reader.readLine())
    assertEquals("b", reader.readLine())
    assertEquals("c", reader.readLine())

    reader.close()

    assertNull(reader.readLine())
  }

  @Test
  fun `multiple lines from multiple notify`() = timeoutRunBlocking {

    val reader = BuildOutputLineReaderImpl()

    reader.notifyTextAvailable("a\n")
    reader.notifyTextAvailable("b\n")
    reader.notifyTextAvailable("c\n")

    assertEquals("a", reader.readLine())
    assertEquals("b", reader.readLine())
    assertEquals("c", reader.readLine())

    reader.close()

    assertNull(reader.readLine())
  }

  @Test
  fun `text without trailing newline is flushed on close`() = timeoutRunBlocking {
    val reader = BuildOutputLineReaderImpl()

    reader.notifyTextAvailable("partial")

    reader.close()

    assertEquals("partial", reader.readLine())

    assertNull(reader.readLine())
  }

  @Test
  fun `readLine suspends until data arrives`() = timeoutRunBlocking {
    val reader = BuildOutputLineReaderImpl()

    launch {
      assertEquals("delayed", reader.readLine())
    }

    delay(50.milliseconds)

    reader.notifyTextAvailable("delayed\n")

    reader.close()
  }

  @Test
  fun `crlf line endings are stripped`() = timeoutRunBlocking {
    val reader = BuildOutputLineReaderImpl()

    reader.notifyTextAvailable("hello\r\n")

    assertEquals("hello", reader.readLine())

    reader.close()
  }

  @Test
  fun `backpressure writer blocks when channel is full`() = timeoutRunBlocking {
    val reader = BuildOutputLineReaderImpl(linesBufferSize = 2)

    launch {
      // Writing more lines than capacity; the third send should block until someone reads
      reader.notifyTextAvailable("a\n")
      reader.notifyTextAvailable("b\n")
      reader.notifyTextAvailable("c\n")
    }

    assertEquals("a", reader.readLine())
    assertEquals("b", reader.readLine())
    assertEquals("c", reader.readLine())

    reader.close()
  }

  @Test
  fun `test reader cannot be closed twice`(): Unit = timeoutRunBlocking {
    val reader = BuildOutputLineReaderImpl()

    reader.close()

    assertThrows<IllegalStateException> {
      reader.close()
    }
  }

  @Test
  fun `test text cannot be notified to closed reader`(): Unit = timeoutRunBlocking {
    val reader = BuildOutputLineReaderImpl()

    reader.close()

    assertThrows<IllegalStateException> {
      reader.notifyTextAvailable("text")
    }
  }
}
