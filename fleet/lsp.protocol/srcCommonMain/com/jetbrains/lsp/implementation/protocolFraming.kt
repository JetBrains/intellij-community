package com.jetbrains.lsp.implementation

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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonElement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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

/** The longest header line [withLspFraming] and [withBaseProtocolFraming] read, in bytes; a longer one is rejected. */
internal const val MAX_HEADER_LINE_LENGTH: Int = 8 shl 10

/**
 * The default largest frame body, in bytes, of [withLspFraming] and [withBaseProtocolFraming]: a frame whose
 * `Content-Length` is larger is rejected before any of its body is read.
 */
const val DEFAULT_MAX_BODY_SIZE: Int = 256 shl 20

/**
 * Runs [body] over the base-protocol frames of [connection]: `Content-Length` header, blank line, UTF-8 JSON body.
 *
 * [body] gets the decoded incoming messages and a channel for outgoing messages, both as JSON trees. For an LSP
 * session use [withLspFraming], or the connection overloads of [withLsp] and [serveLsp]. Messages queued before [body]
 * returns are written before the connection closes, for at most a few seconds.
 *
 * A header line of more than [MAX_HEADER_LINE_LENGTH] bytes, or a `Content-Length` above [maxBodySize], drops the
 * connection, as any header that is not ours does.
 */
suspend fun withBaseProtocolFraming(
  connection: LspConnection,
  exitSignal: CompletableDeferred<Unit>? = null,
  maxBodySize: Int = DEFAULT_MAX_BODY_SIZE,
  body: suspend CoroutineScope.(
    incoming: ReceiveChannel<JsonElement>,
    outgoing: SendChannel<JsonElement>,
  ) -> Unit,
) {
  withFraming(connection, exitSignal, maxBodySize, decode = LspWireCodec::decodeBody, write = { message -> writeByteArray(LspWireCodec.encodeFrame(message)) }, body)
}

/**
 * [withBaseProtocolFraming] over wire messages: the same frames, reader and header rules, but an incoming body becomes
 * no tree ([LspWireBody]: its text, read by the wire [withLsp], where kotlinx reads the envelope and the payload, typed
 * by the handler's serializer, straight from the text), and outgoing messages are ready frame parts ([LspWireOutgoing]),
 * written as they are. Use it with the channel overloads of [withLsp] and [serveLsp].
 *
 * NOT A STABLE API, see [LspWireCodec].
 */
suspend fun withLspFraming(
  connection: LspConnection,
  exitSignal: CompletableDeferred<Unit>? = null,
  maxBodySize: Int = DEFAULT_MAX_BODY_SIZE,
  body: suspend CoroutineScope.(
    incoming: ReceiveChannel<LspWireBody>,
    outgoing: SendChannel<LspWireOutgoing>,
  ) -> Unit,
) {
  withFraming(connection, exitSignal, maxBodySize, decode = LspWireCodec::decodeFrameBody, write = { message -> message.writeTo(writeBuffer) }, body)
}

/** The frame loops: a reader that decodes each body with [decode], a writer that writes each message with [write]. */
private suspend fun <In : Any, Out : Any> withFraming(
  connection: LspConnection,
  exitSignal: CompletableDeferred<Unit>?,
  maxBodySize: Int,
  decode: (ByteArray) -> In,
  write: ByteWriter.(Out) -> Unit,
  body: suspend CoroutineScope.(incoming: ReceiveChannel<In>, outgoing: SendChannel<Out>) -> Unit,
) {
  val reader = connection.input
  val writer = connection.output

  coroutineScope {
    val (incomingSender, incomingReceiver) = channels<In>()
    val (outgoingSender, outgoingReceiver) = channels<Out>(Channel.UNLIMITED)
    val readJob = launch(CoroutineName("frame reader")) {
      incomingSender.use {
        while (true) {
          val frame = reader.readFrame(maxBodySize)
          if (frame == null) {
            exitSignal?.complete(Unit)
            break
          }
          incomingSender.send(decode(frame))
        }
      }
    }
    val writeJob = launch(CoroutineName("frame writer")) {
      outgoingReceiver.consumeEach { message ->
        val success = writer.writeFrame(message, write)
        if (!success) {
          exitSignal?.complete(Unit)
        }
      }
    }

    var returned = false
    try {
      body(incomingReceiver, outgoingSender)
      returned = true
    }
    finally {
      readJob.cancel()
      try {
        if (returned) {
          // write what the body queued before it returned, e.g. the answer to `disconnect`
          outgoingSender.close()
          withTimeoutOrNull(OUTGOING_DRAIN_TIMEOUT) { writeJob.join() }
        }
      }
      finally {
        writeJob.cancel()
        connection.close()
      }
    }
  }
}

/** How long [withFraming] waits to write the queued messages after the body returns: a peer that stops reading must not hold the exit. */
private val OUTGOING_DRAIN_TIMEOUT: Duration = 5.seconds

/**
 * @return the body of the next frame, or `null` at the end of input or on a header that is not ours: a header line of
 *   more than [MAX_HEADER_LINE_LENGTH] bytes, or a `Content-Length` above [maxBodySize], is not ours either.
 *
 * This is the one place that decides what the end of input inside a frame means: wherever the input ends (inside a
 * header line, right after a CR, after the headers, inside the body), the frame is dropped and the input ends
 * cleanly, with one INFO line. The read primitives only report what they saw.
 */
private suspend fun ByteReader.readFrame(maxBodySize: Int): ByteArray? {
  var contentLength = -1
  var readSomething = false
  try {
    while (true) {
      val line = readLineWithEnd(MAX_HEADER_LINE_LENGTH) ?: return if (readSomething) truncated("after the headers, before the blank line") else null
      if (line.end == LineEnd.TOO_LONG) {
        LOG.warn { "Rejecting connection: a header line longer than $MAX_HEADER_LINE_LENGTH bytes" }
        return null
      }
      if (line.end != LineEnd.NEWLINE) return truncated("inside a header line")
      val text = line.text
      if (text.isEmpty()) break
      readSomething = true
      val colon = text.indexOf(':')
      if (colon < 0 || !isHeaderName(text, colon)) {
        // Not a base-protocol header (e.g. an HTTP request line or an HTTP header such as `Host`).
        // Drop the connection instead of trying to interpret it as a frame.
        LOG.warn { "Rejecting connection: not a valid base-protocol header: ${text.take(80)}" }
        return null
      }
      if (colon == CONTENT_LENGTH.length && text.startsWith(CONTENT_LENGTH)) {
        val value = text.substring(colon + 1).trim()
        contentLength = value.toIntOrNull()?.takeIf { it >= 0 } ?: run {
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
    if (contentLength > maxBodySize) {
      LOG.warn { "Rejecting connection: Content-Length $contentLength is above the limit of $maxBodySize bytes" }
      return null
    }
    val body = readByteArray(contentLength)
    if (body.size < contentLength) return truncated("${body.size} of $contentLength body bytes")
    return body
  }
  catch (_: IOException) {
    return null
  }
}

/** The input ended inside a frame at [where]: drop the frame, end cleanly. */
private fun truncated(where: String): ByteArray? {
  LOG.info { "Input ended inside a frame: $where" }
  return null
}

private const val CONTENT_LENGTH = "Content-Length"
private const val CONTENT_TYPE = "Content-Type"

/** Whether `line[0, colon)` is a header name: the two names the base protocol sends, or else any RFC 7230 token. */
private fun isHeaderName(line: String, colon: Int): Boolean =
  colon == CONTENT_LENGTH.length && line.startsWith(CONTENT_LENGTH) ||
  colon == CONTENT_TYPE.length && line.startsWith(CONTENT_TYPE) ||
  HEADER_NAME_REGEX.matches(line.substring(0, colon))

/**
 * Writes one frame with one flush: [write] puts the frame bytes of [message] into the write buffer.
 *
 * @return whether the frame was written (`true`) or the channel was closed (`false`).
 */
private suspend fun <Out> ByteWriter.writeFrame(message: Out, write: ByteWriter.(Out) -> Unit): Boolean {
  try {
    if (isClosedForWrite) return false
    write(message)
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
