package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.LSP
import fleet.util.decodeToStringUtf8
import fleet.util.encodeToByteArrayUtf8
import fleet.util.logging.KLoggers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonElement

private val LOG = KLoggers.logger("com.jetbrains.lsp.implementation.protocolFraming")

/**
 * A valid base-protocol header field-name (RFC 7230 `token`). The base protocol used by LSP and DAP
 * only ever sends `Content-Length` and `Content-Type`, so any line whose name is not a valid token is
 * not one of our frames.
 *
 * This is what keeps HTTP requests out: an HTTP request always starts with a request line such as
 * `POST /path HTTP/1.1`, whose "name" part (everything before the first `:`) either lacks a colon or
 * contains a space, so it fails this check and the connection is dropped before any body is read. That
 * closes the cross-protocol attack where a malicious web page uses `fetch`/form-POST against the
 * loopback port the server listens on.
 */
private val HEADER_NAME_REGEX = Regex("""[!#$%&'*+\-.^_`|~0-9A-Za-z]+""")

suspend fun withBaseProtocolFraming(
  connection: LspConnection,
  exitSignal: CompletableDeferred<Unit>? = null,
  body: suspend CoroutineScope.(
    incoming: ReceiveChannel<JsonElement>,
    outgoing: SendChannel<JsonElement>,
  ) -> Unit,
) {
  val reader = connection.input
  val writer = connection.output

  coroutineScope {
    val (incomingSender, incomingReceiver) = channels<JsonElement>()
    val (outgoingSender, outgoingReceiver) = channels<JsonElement>(Channel.UNLIMITED)
    val readJob = launch(CoroutineName("frame reader")) {
      incomingSender.use {
        while (true) {
          val frame = reader.readFrame()
          if (frame == null) {
            exitSignal?.complete(Unit)
            break
          }
          incomingSender.send(frame)
        }
      }
    }
    val writeJob = launch(CoroutineName("frame writer")) {
      outgoingReceiver.consumeEach { frame ->
        val success = writer.writeFrame(frame)
        if (!success) {
          exitSignal?.complete(Unit)
        }
      }
    }

    try {
      body(incomingReceiver, outgoingSender)
    }
    finally {
      readJob.cancel()
      writeJob.cancel()
      connection.close()
    }
  }
}

private suspend fun ByteReader.readFrame(): JsonElement? {
  var contentLength = -1
  var readSomething = false
  val buf = try {
    while (!isClosedForRead) {
      val line = readUTF8Line()
      if (line.isNullOrEmpty()) break
      readSomething = true
      val colon = line.indexOf(':')
      val name = if (colon >= 0) line.substring(0, colon) else line
      if (colon < 0 || !HEADER_NAME_REGEX.matches(name)) {
        // Not a base-protocol header (e.g. an HTTP request line or an HTTP header such as `Host`).
        // Drop the connection instead of trying to interpret it as a frame.
        LOG.warn { "Rejecting connection: not a valid base-protocol header: ${line.take(80)}" }
        return null
      }
      if (name == "Content-Length") {
        val value = line.substring(colon + 1).trim()
        contentLength = value.toIntOrNull() ?: run {
          LOG.warn { "Rejecting connection: invalid Content-Length: $value" }
          return null
        }
      }
    }
    if (!readSomething) return null
    if (contentLength == -1) {
      LOG.warn { "Rejecting connection: Content-Length header not found" }
      return null
    }
    readByteArray(contentLength)
  }
  catch (_: IOException) {
    return null
  }
  val jsonStr = buf.decodeToStringUtf8()
  return try {
    LSP.json.decodeFromString(JsonElement.serializer(), jsonStr)
  }
  catch (x: Throwable) {
    throw IllegalStateException("could not decode json: $jsonStr", x)
  }
}

/**
 * @return Boolean indicating whether the frame was successfully written (`true`) or the channel was closed (`false`).
 */
private suspend fun ByteWriter.writeFrame(jsonElement: JsonElement): Boolean {
  val str = LSP.json.encodeToString(JsonElement.serializer(), jsonElement)
  val frameStr = buildString {
    // protocol requires string length in bytes
    val contentLengthInBytes = str.encodeToByteArrayUtf8().size
    append("Content-Length: $contentLengthInBytes\r\n")
    append("\r\n")
    append(str)
  }
  try {
    if (isClosedForWrite) return false
    writeByteArray(frameStr.encodeToByteArrayUtf8())
    flush()
    return true
  }
  catch (e: Exception) {
    when (e) {
      is IOException -> return false
      else -> throw e
    }
  }
}
