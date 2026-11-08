package com.jetbrains.lsp.test

import com.jetbrains.lsp.implementation.ByteWriter
import com.jetbrains.lsp.implementation.DEFAULT_MAX_BODY_SIZE
import com.jetbrains.lsp.implementation.LspConnection
import com.jetbrains.lsp.implementation.LspException
import com.jetbrains.lsp.implementation.lspHandlers
import com.jetbrains.lsp.implementation.throwLspError
import com.jetbrains.lsp.implementation.withBaseProtocolFraming
import com.jetbrains.lsp.implementation.withLsp
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.implementation.LspClient
import com.jetbrains.lsp.implementation.LspHandlers
import com.jetbrains.lsp.implementation.LspWireCodec
import com.jetbrains.lsp.implementation.LspWireBody
import com.jetbrains.lsp.implementation.LspWireIncoming
import com.jetbrains.lsp.implementation.LspWireOutgoing
import com.jetbrains.lsp.implementation.inMemoryLspConnections
import com.jetbrains.lsp.implementation.withLspFraming
import com.jetbrains.lsp.protocol.StringOrInt
import com.jetbrains.lsp.protocol.NotificationType
import com.jetbrains.lsp.protocol.RequestType
import com.jetbrains.lsp.protocol.ResponseError
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.io.Sink
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

  @Test
  fun `reads frames whose header and body are split across reads at every position`() {
    runTest {
      val first = """{"jsonrpc":"2.0","method":"a/é","params":{"text":"日本 😀"}}"""
      val second = """{"jsonrpc":"2.0","id":"q\"1","result":null}"""
      val raw = frame(first) + "Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n" + frame(second)
      val bytes = raw.encodeToByteArray()
      val expected = listOf(parse(first), parse(second))
      for (at in 1 until bytes.size) {
        assertEquals(expected, readChunkedFrames(bytes.copyOfRange(0, at), bytes.copyOfRange(at, bytes.size)), "split at $at")
      }
      assertEquals(expected, readChunkedFrames(*bytes.map { byteArrayOf(it) }.toTypedArray()), "byte by byte")
    }
  }

  @Test
  fun `reads a frame with extra token headers and LF-only line endings`() {
    runTest {
      val json = """{"jsonrpc":"2.0","method":"ping"}"""
      val size = json.encodeToByteArray().size
      assertEquals(
        listOf(parse(json)),
        readChunkedFrames("X-Trace_id.1: abc\r\nContent-Length: $size\r\nContent-Type: application/json\r\n\r\n$json".encodeToByteArray()),
      )
      assertEquals(listOf(parse(json)), readChunkedFrames("Content-Length: $size\n\n$json".encodeToByteArray()))
      assertEquals(listOf(parse(json)), readChunkedFrames("Content-Length:$size   \r\n\r\n$json".encodeToByteArray()))
    }
  }

  @Test
  fun `rejects headers the base protocol never sends`() {
    runTest {
      val json = """{"jsonrpc":"2.0","method":"ping"}"""
      val size = json.encodeToByteArray().size
      for (header in listOf(
        "Content-Length : $size",       // space in the name
        "content-length: $size",        // another name: no Content-Length at all
        "Content-Length: x$size",       // not a number
        "Content-Length: -$size",       // negative
        "Content Length: $size",        // not a token
        "Content-Length",               // no colon
        "Contént-Length: $size",        // not a token
        ": $size",                      // empty name
        // the names the parser knows are still checked as tokens
        "Content-Length : 1\r\nContent-Length: $size",
        "Content-Length\t: 1\r\nContent-Length: $size",
        "Content-Type : x\r\nContent-Length: $size",
        "Content-Type\u0000: x\r\nContent-Length: $size",
      )) {
        assertEquals(emptyList(), readChunkedFrames("$header\r\n\r\n$json".encodeToByteArray()), header)
      }
    }
  }

  @Test
  fun `a huge Content-Length with a short body ends cleanly`() {
    runTest {
      val json = """{"jsonrpc":"2.0","method":"ping"}"""
      // at the cap, so the body is read (and found short); above it the header alone is rejected
      for (size in listOf(DEFAULT_MAX_BODY_SIZE, Int.MAX_VALUE)) {
        val raw = "Content-Length: $size\r\n\r\n$json".encodeToByteArray()
        assertEquals(emptyList(), readChunkedFrames(raw), "$size")
        assertEquals(emptyList(), readChunkedWireFrames(raw), "$size")
      }
    }
  }

  @Test
  fun `a header line of more than 8 KB drops the connection, also split across reads`() {
    runTest {
      val json = """{"jsonrpc":"2.0","method":"ping"}"""
      val name = "X-Pad: "
      for ((length, frames) in listOf(8192 to 1, 8193 to 0)) {
        val header = name + "a".repeat(length - name.length)
        for (ending in listOf("\r\n", "\n")) {
          val raw = "$header$ending${frame(json)}".encodeToByteArray()
          val expected = List(frames) { parse(json) }
          assertEquals(expected, readChunkedFrames(raw), "length $length")
          assertEquals(expected, readChunkedWireFrames(raw).map { it.json() }, "wire, length $length")
          val split = raw.size / 2
          assertEquals(expected, readChunkedWireFrames(raw.copyOfRange(0, split), raw.copyOfRange(split, raw.size)).map { it.json() },
                       "wire, length $length, split")
          assertEquals(expected, readChunkedWireFrames(*raw.map { byteArrayOf(it) }.toTypedArray()).map { it.json() },
                       "wire, length $length, byte by byte")
        }
      }
      // the line is rejected once 8193 bytes of it are read, whatever follows: no end of it is awaited
      val endless = ("X-Pad: " + "a".repeat(9000)).encodeToByteArray()
      assertEquals(emptyList(), readChunkedWireFrames(endless))
    }
  }

  @Test
  fun `a Content-Length above the body size limit drops the connection`() {
    runTest {
      val json = """{"jsonrpc":"2.0","method":"ping"}"""
      val size = json.encodeToByteArray().size
      val raw = (frame(json) + frame(json)).encodeToByteArray()
      for ((limit, frames) in listOf(size to 2, size - 1 to 0)) {
        val received = mutableListOf<LspWireBody>()
        val exitSignal = CompletableDeferred<Unit>()
        withLspFraming(ChunkedConnection(ChunkedByteReader(raw)), exitSignal, maxBodySize = limit) { incoming, _ ->
          incoming.consumeEach { received.add(it) }
        }
        assertTrue(exitSignal.isCompleted, "limit $limit")
        assertEquals(List(frames) { parse(json) }, received.map { it.json() }, "limit $limit")
        val trees = mutableListOf<JsonElement>()
        withBaseProtocolFraming(ChunkedConnection(ChunkedByteReader(raw)), CompletableDeferred(), maxBodySize = limit) { incoming, _ ->
          incoming.consumeEach { trees.add(it) }
        }
        assertEquals(List(frames) { parse(json) }, trees, "tree, limit $limit")
      }
    }
  }

  @Test
  fun `rejects a CR not followed by LF in the header`() {
    runTest {
      assertEquals(emptyList(), readChunkedFrames("Content-Length: 2\r".encodeToByteArray(), "x\r\n\r\n{}".encodeToByteArray()))
    }
  }

  @Test
  fun `ends cleanly when the input ends right after a CR in the header`() {
    runTest {
      val json = """{"jsonrpc":"2.0","method":"ping"}"""
      val first = frame(json)
      for (raw in listOf("Content-Length: 2\r", "\r", "$first\r", "${first}Content-Length: 2\r\n\r")) {
        val expected = if (raw.startsWith(first)) listOf(parse(json)) else emptyList()
        assertEquals(expected, readChunkedFrames(raw.encodeToByteArray()), raw)
        assertEquals(expected, readChunkedWireFrames(raw.encodeToByteArray()).map { it.json() }, raw)
        val bytes = raw.encodeToByteArray()
        assertEquals(expected, readChunkedFrames(bytes.copyOfRange(0, bytes.size - 1), byteArrayOf(bytes.last())), "CR in its own read: $raw")
      }
      assertEquals(emptyList(), readFrames("Content-Length: 2\r"), "ktor ByteChannel")
    }
  }

  @Test
  fun `writes JSON element frames with the UTF-8 Content-Length`() {
    runTest {
      val message = parse("""{"jsonrpc":"2.0","method":"x/é","params":"日本 😀"}""")
      val expected = frame(LSP.json.encodeToString(JsonElement.serializer(), message)).repeat(2).encodeToByteArray()
      val output = ByteChannel()
      var written = ByteArray(0)
      withBaseProtocolFraming(TestConnection(ByteChannel(), output), exitSignal = CompletableDeferred()) { _, outgoing ->
        outgoing.send(message)
        outgoing.send(message)
        written = output.readByteArray(expected.size)
      }
      assertContentEquals(expected, written)
    }
  }

  @Test
  fun `writes the messages queued right before the body returns`() {
    runTest {
      val messages = (1..3).map { parse("""{"jsonrpc":"2.0","method":"x/note","params":$it}""") }
      val expected = messages.joinToString("") { frame(LSP.json.encodeToString(JsonElement.serializer(), it)) }.encodeToByteArray()
      val output = ByteChannel()
      withBaseProtocolFraming(TestConnection(ByteChannel(), output), exitSignal = CompletableDeferred()) { _, outgoing ->
        for (message in messages) outgoing.send(message)
      }
      // the reader suspends on a short write; virtual time then runs into the timeout instead of hanging
      val written = withTimeout(10_000) { output.readByteArray(expected.size) }
      assertEquals(messages, readChunkedFrames(written))
    }
  }

  @Test
  fun `a peer that does not read does not hold the exit for longer than the drain timeout`() {
    runTest {
      // above the ktor ByteChannel buffer, so the flush suspends until someone reads
      val big = parse("""{"jsonrpc":"2.0","method":"x/note","params":"${"a".repeat(2 shl 20)}"}""")
      withBaseProtocolFraming(TestConnection(ByteChannel(), ByteChannel()), exitSignal = CompletableDeferred()) { _, outgoing ->
        outgoing.send(big)
      }
      assertEquals(5_000, testScheduler.currentTime)
    }
  }

  @Test
  fun `withLsp over framing round-trips requests, results, errors and notifications`() {
    runTest {
      val echo = RequestType("x/\"echo\" é", String.serializer(), String.serializer().nullable, String.serializer())
      val fail = RequestType("x/fail", String.serializer(), String.serializer(), String.serializer())
      val note = NotificationType("x/note ü", String.serializer())
      val notes = Channel<String>(Channel.UNLIMITED)
      val clientToServer = ByteChannel()
      val serverToClient = ByteChannel()
      val server = launch {
        withBaseProtocolFraming(TestConnection(clientToServer, serverToClient)) { incoming, outgoing ->
          withLspOverTrees(incoming, outgoing, lspHandlers {
            request(echo) { text -> if (text == "null") null else "$text 😀" }
            request(fail) { text -> throwLspError(fail, text, "data \"$text\"", -32001) }
            notification(note) { text -> notes.send(text) }
          }) { awaitCancellation() }
        }
      }
      withBaseProtocolFraming(TestConnection(serverToClient, clientToServer)) { incoming, outgoing ->
        withLspOverTrees(incoming, outgoing, lspHandlers {}) { client ->
          assertEquals("日本 \"q\" 😀", client.request(echo, "日本 \"q\""))
          assertEquals(null, client.request(echo, "null"))
          val error = assertFailsWith<LspException> { client.request(fail, "no ü") }
          assertEquals(-32001, error.errorCode)
          assertEquals("no ü", error.message)
          assertEquals("data \"no ü\"", error.payload)
          client.notify(note, "first é")
          client.notifyAsync(note, "second 😀")
          assertEquals("first é", notes.receive())
          assertEquals("second 😀", notes.receive())
        }
      }
      server.cancelAndJoin()
    }
  }

  @Test
  fun `withLspFraming reads the frames withBaseProtocolFraming reads, split at every position`() {
    runTest {
      val first = """{"jsonrpc":"2.0","method":"a/é","params":{"text":"日本 😀"}}"""
      val second = """{"jsonrpc":"2.0","id":"q\"1","result":null}"""
      val raw = frame(first) + "Content-Type: application/vscode-jsonrpc; charset=utf-8\r\n" + frame(second)
      val bytes = raw.encodeToByteArray()
      val expected = listOf(parse(first), parse(second))
      for (at in 1 until bytes.size) {
        val chunks = arrayOf(bytes.copyOfRange(0, at), bytes.copyOfRange(at, bytes.size))
        assertEquals(readChunkedFrames(*chunks), readChunkedWireFrames(*chunks).map { it.json() }, "split at $at")
        assertEquals(expected, readChunkedWireFrames(*chunks).map { it.json() }, "split at $at")
      }
      val messages = readChunkedWireFrames(bytes).map { it.read() }
      assertEquals(listOf(LspWireIncoming.Kind.Notification, LspWireIncoming.Kind.Response), messages.map { it.kind })
      assertEquals("a/é", messages[0].method)
      assertEquals(StringOrInt.string("q\"1"), messages[1].id)
    }
  }

  @Test
  fun `withLspFraming rejects what withBaseProtocolFraming rejects`() {
    runTest {
      val json = """{"jsonrpc":"2.0","method":"ping"}"""
      val size = json.encodeToByteArray().size
      for (raw in listOf(
        "POST /a:b HTTP/1.1\r\nHost: 127.0.0.1:9999\r\nContent-Length: $size\r\n\r\n$json",
        "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n",
        "not-a-header\r\n\r\n",
        "Content-Length : $size\r\n\r\n$json",
        "content-length: $size\r\n\r\n$json",
        "Content-Length: x$size\r\n\r\n$json",
        "Content-Type : x\r\nContent-Length: $size\r\n\r\n$json",
        "Content-Length: 2\rx\r\n\r\n{}",
      )) {
        assertEquals(emptyList(), readChunkedFrames(raw.encodeToByteArray()), raw)
        assertEquals(emptyList(), readChunkedWireFrames(raw.encodeToByteArray()), raw)
      }
    }
  }

  @Test
  fun `withLspFraming fails on a body that is not JSON, like withBaseProtocolFraming`() {
    runTest {
      val raw = frame("""{"jsonrpc":"2.0","method":"ping",}""").encodeToByteArray()
      val old = assertFailsWith<IllegalStateException> { readChunkedFrames(raw) }
      // the wire reader makes the body; its read (on the `withLsp` loop) fails
      val new = assertFailsWith<IllegalStateException> { readChunkedWireFrames(raw).forEach { it.read() } }
      assertEquals(old.message, new.message)
    }
  }

  @Test
  fun `a body that is not JSON fails a withLsp session and closes the connection, on the wire and the tree path`() {
    runTest {
      // Before S1 the wire frame reader failed on such a body; now the first envelope read on the `withLsp` loop does.
      // Either way: the session throws the same IllegalStateException and the connection is closed (no id: no reply).
      val note = NotificationType("x/note", String.serializer())
      val bad = """{"jsonrpc":"2.0","method":"x/note",}"""
      val raw = (frame("""{"jsonrpc":"2.0","method":"x/note","params":"n1"}""") + frame(bad) +
                 frame("""{"jsonrpc":"2.0","method":"x/note","params":"n2"}""")).encodeToByteArray()
      val messages = mutableListOf<String>()
      for (wire in listOf(false, true)) {
        val notes = mutableListOf<String>()
        val output = ByteChannel()
        val connection = ClosingConnection(ChunkedByteReader(raw), output)
        val handlers = lspHandlers { notification(note) { notes.add(it) } }
        val x = assertFailsWith<IllegalStateException>("wire $wire") {
          if (wire) withLsp(connection, exitSignal = null, handlers) { awaitCancellation() }
          else withBaseProtocolFraming(connection) { incoming, outgoing -> withLspOverTrees(incoming, outgoing, handlers) { awaitCancellation() } }
        }
        messages.add(x.message!!)
        assertTrue(connection.closed, "connection closed, wire $wire")
        assertTrue("n2" !in notes, "nothing after the bad body is handled, wire $wire")
        if (wire) assertEquals(listOf("n1"), notes, "the wire loop handles every message before the bad body")
      }
      assertEquals("could not decode json: $bad", messages[0])
      assertEquals(messages[0], messages[1])
    }
  }

  @Test
  fun `withLspFraming writes frames and tree messages with the UTF-8 Content-Length`() {
    runTest {
      val echo = RequestType("x/echo", String.serializer(), String.serializer(), String.serializer())
      val output = ByteChannel()
      val input = ByteChannel()
      var written = ""
      withLspFraming(TestConnection(input, output), exitSignal = CompletableDeferred()) { incoming, outgoing ->
        val server = launch { withLsp(incoming, outgoing, lspHandlers { request(echo) { text -> "$text 😀" } }) { awaitCancellation() } }
        val request = """{"jsonrpc":"2.0","id":7,"method":"x/echo","params":"日本"}"""
        input.writeStringUtf8(frame(request))
        input.flush()
        val expectedBody = """{"jsonrpc":"2.0","id":7,"result":"日本 😀"}"""
        written = output.readByteArray(frame(expectedBody).encodeToByteArray().size).decodeToString()
        server.cancelAndJoin()
      }
      assertEquals(frame("""{"jsonrpc":"2.0","id":7,"result":"日本 😀"}"""), written)
    }
  }

  @Test
  fun `withLspFraming writes each message as exactly its frame bytes`() {
    runTest {
      val echo = RequestType("x/echo é", String.serializer(), String.serializer(), String.serializer())
      val messages = listOf(
        LspWireCodec.encodeRequestFrame(StringOrInt.string("q\"1 ü"), echo.method, echo.paramsSerializer, "日本 😀"),
        LspWireCodec.encodeResultFrame(StringOrInt.int(7), echo.resultSerializer, "ß"),
        LspWireCodec.encodeNotificationFrame("x/note", String.serializer(), "plain"),
        LspWireCodec.encodeErrorFrame(StringOrInt.int(8), ResponseError(code = -32001, message = "Ошибка ∅")),
      )
      val frames = messages.map { it.frame() }
      for ((message, bytes) in messages.zip(frames)) {
        val body = LSP.json.encodeToString(JsonElement.serializer(), message.json())
        assertEquals(frame(body), bytes.decodeToString())
      }
      val expected = frames.reduce { acc, frame -> acc + frame }
      val output = ByteChannel()
      var written = ByteArray(0)
      withLspFraming(TestConnection(ByteChannel(), output), exitSignal = CompletableDeferred()) { _, outgoing ->
        for (message in messages) outgoing.send(message)
        // the reader suspends on a short write; virtual time then runs into the timeout instead of hanging
        written = withTimeout(10_000) { output.readByteArray(expected.size) }
      }
      assertContentEquals(expected, written)
      assertEquals(messages.map { it.json() }, readChunkedWireFrames(written).map { it.json() })
    }
  }

  @Test
  fun `connection-level withLsp round-trips requests, results, errors and notifications`() {
    runTest {
      val (serverConnection, clientConnection) = inMemoryLspConnections()
      val notes = Channel<String>(Channel.UNLIMITED)
      val server = launch {
        withLsp(serverConnection, exitSignal = null, testHandlers(notes)) { awaitCancellation() }
      }
      withLsp(clientConnection, exitSignal = null, lspHandlers {}) { client ->
        exerciseServer(client, notes)
      }
      server.cancelAndJoin()
    }
  }

  @Test
  fun `old JsonElement client talks to a wire server and a wire client to an old server`() {
    runTest {
      val notes = Channel<String>(Channel.UNLIMITED)
      val clientToServer = ByteChannel()
      val serverToClient = ByteChannel()
      val server = launch {
        withLsp(TestConnection(clientToServer, serverToClient), exitSignal = null, testHandlers(notes)) { awaitCancellation() }
      }
      withBaseProtocolFraming(TestConnection(serverToClient, clientToServer)) { incoming, outgoing ->
        withLspOverTrees(incoming, outgoing, lspHandlers {}) { client -> exerciseServer(client, notes) }
      }
      server.cancelAndJoin()

      val clientToOldServer = ByteChannel()
      val oldServerToClient = ByteChannel()
      val oldServer = launch {
        withBaseProtocolFraming(TestConnection(clientToOldServer, oldServerToClient)) { incoming, outgoing ->
          withLspOverTrees(incoming, outgoing, testHandlers(notes)) { awaitCancellation() }
        }
      }
      withLsp(TestConnection(oldServerToClient, clientToOldServer), exitSignal = null, lspHandlers {}) { client ->
        exerciseServer(client, notes)
      }
      oldServer.cancelAndJoin()
    }
  }

  @Test
  fun `withLsp over wire channels sends frames whose tree is built on demand`() {
    runTest {
      val echo = RequestType("x/echo", String.serializer(), String.serializer(), String.serializer())
      val note = NotificationType("x/note", String.serializer())
      val toServer = Channel<LspWireBody>(Channel.UNLIMITED)
      val fromServer = Channel<LspWireOutgoing>(Channel.UNLIMITED)
      val notes = Channel<String>(Channel.UNLIMITED)
      val server = launch {
        withLsp(toServer, fromServer, lspHandlers {
          request(echo) { text -> "$text!" }
          notification(note) { text -> notes.send(text) }
        }) { client ->
          client.notify(note, "hello é")
          awaitCancellation()
        }
      }
      val hello = fromServer.receive()
      assertEquals(parse("""{"jsonrpc":"2.0","method":"x/note","params":"hello é"}"""), hello.json())
      val helloBody = LSP.json.encodeToString(JsonElement.serializer(), hello.json())
      assertEquals(frame(helloBody), hello.frame().decodeToString())
      toServer.send(LspWireCodec.decodeFrameBody("""{"jsonrpc":"2.0","method":"x/note","params":"n1"}""".encodeToByteArray()))
      toServer.send(LspWireCodec.decodeFrameBody("""{"jsonrpc":"2.0","id":3,"method":"x/echo","params":"é"}""".encodeToByteArray()))
      assertEquals("n1", notes.receive())
      val response = fromServer.receive()
      assertEquals(parse("""{"jsonrpc":"2.0","id":3,"result":"é!"}"""), response.json())
      assertTrue(response.json() === response.json(), "cached")
      server.cancelAndJoin()
    }
  }

  @Test
  fun `withLsp over wire channels reads bodies made from trees and answers with frames that parse back to trees`() {
    runTest {
      val echo = RequestType("x/echo", String.serializer(), String.serializer(), String.serializer())
      val toServer = Channel<LspWireBody>(Channel.UNLIMITED)
      val fromServer = Channel<LspWireOutgoing>(Channel.UNLIMITED)
      val server = launch {
        withLsp(toServer, fromServer, lspHandlers { request(echo) { text -> "$text!" } }) { awaitCancellation() }
      }
      toServer.send(LspWireCodec.decodeFrameBody(parse("""{"jsonrpc":"2.0","id":"a","method":"x/echo","params":"é"}""").toString().encodeToByteArray()))
      assertEquals(parse("""{"jsonrpc":"2.0","id":"a","result":"é!"}"""), fromServer.receive().json())
      server.cancelAndJoin()
    }
  }

  @Test
  fun `connection-level withLsp encodes and decodes payloads as text, with no tree`() {
    runTest {
      val modes = probeModes(
        server = { input, output, handlers ->
          withLsp(TestConnection(input, output), exitSignal = null, handlers) { awaitCancellation() }
        },
        client = { input, output, body -> withLsp(TestConnection(input, output), exitSignal = null, lspHandlers {}, body = body) },
      )
      assertEquals(listOf("decode text", "decode text", "encode text", "encode text"), modes)
    }
  }

  @Test
  fun `withLsp over withLspFraming channels encodes and decodes payloads as text`() {
    runTest {
      val modes = probeModes(
        server = { input, output, handlers ->
          withLspFraming(TestConnection(input, output)) { incoming, outgoing ->
            withLsp(incoming, outgoing, handlers) { awaitCancellation() }
          }
        },
        client = { input, output, body ->
          withLspFraming(TestConnection(input, output)) { incoming, outgoing -> withLsp(incoming, outgoing, lspHandlers {}, body = body) }
        },
      )
      assertEquals(listOf("decode text", "decode text", "encode text", "encode text"), modes)
    }
  }

  @Test
  fun `withLsp over withBaseProtocolFraming channels encodes and decodes payloads as text`() {
    runTest {
      val modes = probeModes(
        server = { input, output, handlers ->
          withBaseProtocolFraming(TestConnection(input, output)) { incoming, outgoing ->
            withLspOverTrees(incoming, outgoing, handlers) { awaitCancellation() }
          }
        },
        client = { input, output, body ->
          withBaseProtocolFraming(TestConnection(input, output)) { incoming, outgoing ->
            withLspOverTrees(incoming, outgoing, lspHandlers {}, body = body)
          }
        },
      )
      assertEquals(listOf("decode text", "decode text", "encode text", "encode text"), modes)
    }
  }

  /**
   * One request from a client to a server, both run by the given entry points over in-memory frames. Returns how each
   * payload was encoded and decoded ([ModeProbe]): the params and the result, on both sides, sorted.
   */
  private suspend fun CoroutineScope.probeModes(
    server: suspend (input: ByteChannel, output: ByteChannel, handlers: LspHandlers) -> Unit,
    client: suspend (input: ByteChannel, output: ByteChannel, body: suspend CoroutineScope.(LspClient) -> Unit) -> Unit,
  ): List<String> {
    val modes = mutableListOf<String>()
    val probe = RequestType("x/probe", ModeProbe(modes), ModeProbe(modes), String.serializer())
    val clientToServer = ByteChannel()
    val serverToClient = ByteChannel()
    val serverJob = launch { server(clientToServer, serverToClient, lspHandlers { request(probe) { text -> "$text!" } }) }
    client(serverToClient, clientToServer) { lspClient -> assertEquals("é!", lspClient.request(probe, "é")) }
    serverJob.cancelAndJoin()
    return modes.sorted()
  }

  private val testEcho = RequestType("x/\"echo\" é", String.serializer(), String.serializer().nullable, String.serializer())
  private val testFail = RequestType("x/fail", String.serializer(), String.serializer(), String.serializer())
  private val testNote = NotificationType("x/note ü", String.serializer())

  private fun testHandlers(notes: Channel<String>): LspHandlers = lspHandlers {
    request(testEcho) { text -> if (text == "null") null else "$text 😀" }
    request(testFail) { text -> throwLspError(testFail, text, "data \"$text\"", -32001) }
    notification(testNote) { text -> notes.send(text) }
  }

  /** echo, a failing request and two notifications. */
  private suspend fun exerciseServer(client: LspClient, notes: Channel<String>) {
    assertEquals("日本 \"q\" 😀", client.request(testEcho, "日本 \"q\""))
    assertEquals(null, client.request(testEcho, "null"))
    val error = assertFailsWith<LspException> { client.request(testFail, "no ü") }
    assertEquals(-32001, error.errorCode)
    assertEquals("no ü", error.message)
    assertEquals("data \"no ü\"", error.payload)
    client.notify(testNote, "first é")
    client.notifyAsync(testNote, "second 😀")
    assertEquals("first é", notes.receive())
    assertEquals("second 😀", notes.receive())
  }

  /**
   * [withLsp] over tree channels (from [withBaseProtocolFraming]), as the dropped `JsonElement` overload ran it: each
   * incoming tree becomes a body from its compact text, and each outgoing frame is parsed back into a tree.
   */
  private suspend fun withLspOverTrees(
    incoming: ReceiveChannel<JsonElement>,
    outgoing: SendChannel<JsonElement>,
    handlers: LspHandlers,
    body: suspend CoroutineScope.(LspClient) -> Unit,
  ): Unit = coroutineScope {
    val bodies = Channel<LspWireBody>()
    val frames = Channel<LspWireOutgoing>(Channel.UNLIMITED)
    val reader = launch {
      try {
        incoming.consumeEach { bodies.send(LspWireCodec.decodeFrameBody(it.toString().encodeToByteArray())) }
      }
      finally {
        bodies.close()
      }
    }
    launch { for (frame in frames) outgoing.send(frame.json()) }
    try {
      withLsp(bodies, frames, handlers, body = body)
    }
    finally {
      frames.close()
      reader.cancel()
    }
  }

  /** [readChunkedFrames] through [withLspFraming]; also checks that the end of input completes the exit signal. */
  private suspend fun readChunkedWireFrames(vararg chunks: ByteArray): List<LspWireBody> {
    val received = mutableListOf<LspWireBody>()
    val exitSignal = CompletableDeferred<Unit>()
    withLspFraming(ChunkedConnection(ChunkedByteReader(*chunks)), exitSignal = exitSignal) { incoming, _ ->
      incoming.consumeEach { received.add(it) }
    }
    assertTrue(exitSignal.isCompleted)
    return received
  }

  private fun frame(json: String): String = "Content-Length: ${json.encodeToByteArray().size}\r\n\r\n$json"

  private fun parse(json: String): JsonElement = Json.parseToJsonElement(json)

  /** Feeds [chunks], one per read, through the real base-protocol framing and returns every frame delivered. */
  private suspend fun readChunkedFrames(vararg chunks: ByteArray): List<JsonElement> {
    val received = mutableListOf<JsonElement>()
    withBaseProtocolFraming(ChunkedConnection(ChunkedByteReader(*chunks)), exitSignal = CompletableDeferred()) { incoming, _ ->
      incoming.consumeEach { received.add(it) }
    }
    return received
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

/**
 * A string serializer that records, in [modes], whether kotlinx ran it from text (its streaming JSON encoder or
 * decoder) or over a [JsonElement] tree. Only the text path skips the tree.
 */
private class ModeProbe(private val modes: MutableList<String>) : KSerializer<String> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ModeProbe", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: String) {
    modes.add("encode ${mode(encoder)}")
    encoder.encodeString(value)
  }

  override fun deserialize(decoder: Decoder): String {
    modes.add("decode ${mode(decoder)}")
    return decoder.decodeString()
  }

  private fun mode(coder: Any): String = if (coder::class.simpleName.orEmpty().startsWith("Streaming")) "text" else "tree"
}

private class TestConnection(inputChannel: ByteChannel, outputChannel: ByteChannel) : LspConnection {
  override val input = ByteChannelReader(inputChannel)
  override val output: ByteWriter = ByteChannelWriter(outputChannel)
  override fun isAlive(): Boolean = true
  override fun close() {}
}

private class ClosingConnection(override val input: ChunkedByteReader, output: ByteChannel) : LspConnection {
  override val output: ByteWriter = ByteChannelWriter(output)
  var closed = false
  override fun isAlive(): Boolean = true
  override fun close() {
    closed = true
  }
}

private class ChunkedConnection(override val input: ChunkedByteReader) : LspConnection {
  override val output: ByteWriter = ByteChannelWriter(ByteChannel())
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
