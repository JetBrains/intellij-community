// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.output

import com.intellij.testFramework.common.timeoutRunBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BuildOutputReplayableLineReaderTest {

  @Test
  fun `reads lines in order`() = timeoutRunBlocking {
    val reader = BuildOutputReplayableLineReaderImpl()

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
  fun `pushBack(1) replays the last line`() = timeoutRunBlocking {
    val reader = BuildOutputReplayableLineReaderImpl()

    reader.notifyTextAvailable("a\n")
    reader.notifyTextAvailable("b\n")

    assertEquals("a", reader.readLine())

    reader.pushBack(1)

    assertEquals("a", reader.readLine())
    assertEquals("b", reader.readLine())

    reader.close()
  }

  @Test
  fun `pushBack(n) replays last n lines in order`() = timeoutRunBlocking {
    val reader = BuildOutputReplayableLineReaderImpl()

    reader.notifyTextAvailable("a\n")
    reader.notifyTextAvailable("b\n")
    reader.notifyTextAvailable("c\n")

    assertEquals("a", reader.readLine())
    assertEquals("b", reader.readLine())
    assertEquals("c", reader.readLine())

    reader.pushBack(2)

    assertEquals("b", reader.readLine())
    assertEquals("c", reader.readLine())

    reader.close()
  }

  @Test
  fun `pushBack(0) is a no-op`() = timeoutRunBlocking {
    val reader = BuildOutputReplayableLineReaderImpl()

    reader.notifyTextAvailable("a\n")
    reader.notifyTextAvailable("b\n")

    assertEquals("a", reader.readLine())

    reader.pushBack(0)

    assertEquals("b", reader.readLine())

    reader.close()
  }

  @Test
  fun `pushBack beyond buffer size truncates to available`() = timeoutRunBlocking {
    val reader = BuildOutputReplayableLineReaderImpl(pushBackBufferSize = 2)

    reader.notifyTextAvailable("a\n")
    reader.notifyTextAvailable("b\n")
    reader.notifyTextAvailable("c\n")

    // Only last `bufferSize` are in the push-back history
    assertEquals("a", reader.readLine())
    assertEquals("b", reader.readLine())
    assertEquals("c", reader.readLine())

    reader.pushBack(100)

    assertEquals("b", reader.readLine())
    assertEquals("c", reader.readLine())

    reader.close()
  }

  @Test
  fun `EOF is visible after pushBack completes`() = timeoutRunBlocking {
    val reader = BuildOutputReplayableLineReaderImpl()

    reader.notifyTextAvailable("a\n")

    reader.close()

    assertEquals("a", reader.readLine())

    assertNull(reader.readLine()) // EOF

    reader.pushBack(1)

    assertEquals("a", reader.readLine())

    assertNull(reader.readLine()) // EOF again after replay
  }
}
