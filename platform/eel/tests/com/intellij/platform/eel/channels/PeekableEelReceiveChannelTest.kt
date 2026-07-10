// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EelDelicateApi::class)

package com.intellij.platform.eel.channels

import com.intellij.platform.eel.ReadResult
import com.intellij.platform.eel.provider.utils.EelPipe
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.milliseconds

@Suppress("checkedExceptions")
class PeekableEelReceiveChannelTest {
  private fun delegate(data: ByteArray = ByteArray(0)): EelReceiveChannel = ByteArrayInputStream(data).consumeAsEelChannel()

  private fun ByteBuffer.toList(): List<Byte> = buildList {
    while (hasRemaining()) add(get())
  }

  @Test
  fun `test prepend and receive`() = runBlocking {
    val channel = PeekableEelReceiveChannel(delegate(byteArrayOf(5, 6)))

    channel.prepend(ByteBuffer.wrap(byteArrayOf(1, 2)), ByteBuffer.wrap(byteArrayOf(3, 4)))

    run {
      val dst = ByteBuffer.allocate(3)
      channel.receive(dst) shouldBe ReadResult.NOT_EOF
      dst.flip().toList() shouldBe listOf<Byte>(1, 2, 3)
    }

    run {
      val dst = ByteBuffer.allocate(100)
      while (true) {
        check(dst.hasRemaining())
        when (channel.receive(dst)) {
          ReadResult.EOF -> break
          ReadResult.NOT_EOF -> Unit
        }
      }
      dst.flip().toList() shouldBe listOf<Byte>(4, 5, 6)
    }
  }

  @Test
  fun `test available`() {
    val delegate = delegate(byteArrayOf(1, 2, 3))
    val channel = PeekableEelReceiveChannel(delegate)

    val buf1 = ByteBuffer.wrap(byteArrayOf(4, 5))
    channel.prepend(buf1)

    channel.available() shouldBe 2 + 3
  }

  @Test
  fun `test readLine`() = runBlocking {
    val channel = PeekableEelReceiveChannel(delegate("o\n\nworld".toByteArray()))
    channel.prepend(ByteBuffer.wrap("hell".toByteArray()))

    channel.readLine(StandardCharsets.UTF_8) shouldBe "hello"
    channel.readLine(StandardCharsets.UTF_8) shouldBe ""
    channel.readLine(StandardCharsets.UTF_8) shouldBe "world"
    channel.readLine(StandardCharsets.UTF_8) shouldBe null
  }

  @Test
  fun `test readUntil`() = runBlocking {
    val pipe = EelPipe(prefersDirectBuffers = false)
    launch {
      pipe.sink.send(ByteBuffer.wrap(byteArrayOf(1, 2)))
      delay(50.milliseconds)
      pipe.sink.send(ByteBuffer.wrap(byteArrayOf(3, 4)))
      pipe.sink.send(ByteBuffer.wrap(byteArrayOf(5, 6, 7)))
    }

    val result = StringBuilder()
    val channel = pipe.source.peekable()
    channel.readUntil(6.toByte()) { buffer, last ->
      val data = ByteArray(buffer.remaining()) { buffer.get(it) }.joinToString { it.toUByte().toString() }
      result.append("$data $last\n")
    }
    result.toString() shouldBe """
      1, 2 false
      3, 4 false
      5 true
      
    """.trimIndent()
  }
}
