package com.jetbrains.lsp.test

import com.jetbrains.lsp.implementation.ByteReader
import com.jetbrains.lsp.implementation.readUTF8Line
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.writeByte
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.runBlocking
import kotlinx.io.Source
import kotlin.test.Test
import kotlin.test.assertEquals

private const val LF = 0x0A.toByte()
private const val CR = 0x0D.toByte()

class ByteReaderTest {

  @Test
  fun `readUTF8Line reads an LF-terminated line`() {
    runBlocking {
      val message = "Hello, world!"
      val channel = ByteChannel()
      val reader = ByteChannelReader(channel)

      val line = async { reader.readUTF8Line() }
      channel.writeLine(message, LF)
      channel.flushAndClose()

      assertEquals(message, line.await())
    }
  }

  @Test
  fun `readUTF8Line reads an LF-terminated line sent byte by byte`() {
    runBlocking {
      val message = "Hello, world!"
      val channel = ByteChannel()
      val reader = ByteChannelReader(channel)

      val line = async { reader.readUTF8Line() }
      message.toByteArray(Charsets.UTF_8).forEach { byte ->
        channel.writeByte(byte)
        channel.flush()
      }
      channel.writeByte(LF)
      channel.flushAndClose()

      assertEquals(message, line.await())
    }
  }

  @Test
  fun `readUTF8Line reads a CRLF-terminated line sent byte by byte`() {
    runBlocking {
      val message = "Hello, world!"
      val channel = ByteChannel()
      val reader = ByteChannelReader(channel)

      val line = async { reader.readUTF8Line() }
      message.toByteArray(Charsets.UTF_8).forEach { byte ->
        channel.writeByte(byte)
        channel.flush()
      }
      channel.writeBytes(CR, LF)
      channel.flushAndClose()

      assertEquals(message, line.await())
    }
  }

  @Test
  fun `readUTF8Line reads several LF-terminated lines`() {
    runBlocking {
      val messages = listOf(
        "Hello", "world", "how", "are", "you", "doing"
      )
      val channel = ByteChannel()
      val reader = ByteChannelReader(channel)

      val lines = flow {
        while (!reader.isClosedForRead) {
          emit(reader.readUTF8Line())
        }
      }

      for (message in messages) {
        channel.writeLine(message, LF)
        channel.flush()
      }
      channel.close()

      lines.withIndex().collect { (i, line) ->
        assertEquals(messages[i], line)
      }
    }
  }

  private suspend fun ByteWriteChannel.writeBytes(vararg bytes: Byte) {
    for (byte in bytes) {
      writeByte(byte)
    }
  }

  private suspend fun ByteWriteChannel.writeLine(s: String, vararg terminators: Byte = byteArrayOf(LF)) {
    writeStringUtf8(s)
    for (terminator in terminators) {
      writeByte(terminator)
    }
  }
}

@OptIn(InternalAPI::class)
internal class ByteChannelReader(val underlying: ByteChannel) : ByteReader {
  override val closedCause: Throwable?
    get() = underlying.closedCause
  override val isClosedForRead: Boolean
    get() = underlying.isClosedForRead
  override val readBuffer: Source
    get() = underlying.readBuffer

  override suspend fun awaitContent(min: Int): Boolean {
    return underlying.awaitContent()
  }

  override fun cancel(cause: Throwable?) {
    underlying.cancel(cause)
  }
}