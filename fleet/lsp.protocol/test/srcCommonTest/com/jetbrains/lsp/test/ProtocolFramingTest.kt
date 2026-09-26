package com.jetbrains.lsp.test

import com.jetbrains.lsp.implementation.ByteWriter
import com.jetbrains.lsp.implementation.LspConnection
import com.jetbrains.lsp.implementation.withBaseProtocolFraming
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.test.runTest
import kotlinx.io.Sink
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolFramingTest {

  @Test
  fun `reads a well-formed frame`() {
    runTest {
      val json = """{"jsonrpc":"2.0","method":"ping"}"""
      val frames = readFrames("Content-Length: ${json.encodeToByteArray().size}\r\n\r\n$json")
      assertEquals(1, frames.size)
      assertTrue(frames.single() is JsonObject)
    }
  }

  @Test
  fun `reads a frame with an extra Content-Type header`() {
    runTest {
      val json = """{"jsonrpc":"2.0"}"""
      val frames = readFrames(
        "Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n" +
          "Content-Length: ${json.encodeToByteArray().size}\r\n\r\n$json"
      )
      assertEquals(1, frames.size)
    }
  }

  @Test
  fun `rejects an HTTP request whose path contains a colon`() {
    runTest {
      // A colon in the path used to sneak the request line past the header parser; it must still be rejected.
      val body = """{"jsonrpc":"2.0","method":"pwn"}"""
      val request = "POST /a:b HTTP/1.1\r\n" +
        "Host: 127.0.0.1:9999\r\n" +
        "Content-Type: application/json\r\n" +
        "Content-Length: ${body.encodeToByteArray().size}\r\n\r\n$body"
      assertEquals(emptyList(), readFrames(request))
    }
  }

  @Test
  fun `rejects an HTTP request without a colon in the request line`() {
    runTest {
      assertEquals(emptyList(), readFrames("GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"))
    }
  }

  @Test
  fun `rejects a header line with no colon`() {
    runTest {
      assertEquals(emptyList(), readFrames("not-a-header\r\n\r\n"))
    }
  }

  /** Feeds [raw] through the real base-protocol framing and returns every frame delivered to the server. */
  private suspend fun readFrames(raw: String): List<JsonElement> {
    val inputChannel = ByteChannel()
    inputChannel.writeStringUtf8(raw)
    inputChannel.flushAndClose()

    val received = mutableListOf<JsonElement>()
    withBaseProtocolFraming(
      TestConnection(inputChannel, ByteChannel()),
      exitSignal = CompletableDeferred(),
    ) { incoming: ReceiveChannel<JsonElement>, _ ->
      incoming.consumeEach { received.add(it) }
    }
    return received
  }
}

private class TestConnection(inputChannel: ByteChannel, outputChannel: ByteChannel) : LspConnection {
  override val input = ByteChannelReader(inputChannel)
  override val output: ByteWriter = ByteChannelWriter(outputChannel)
  override fun isAlive(): Boolean = true
  override fun close() {}
}

@OptIn(InternalAPI::class)
private class ByteChannelWriter(private val channel: ByteChannel) : ByteWriter {
  override val isClosedForWrite: Boolean get() = channel.isClosedForWrite
  override val closedCause: Throwable? get() = channel.closedCause
  override val writeBuffer: Sink get() = channel.writeBuffer

  override suspend fun flush(): Unit = channel.flush()
  override suspend fun flushAndClose(): Unit = channel.flushAndClose()
  override fun cancel(cause: Throwable?): Unit = channel.cancel(cause)
}
