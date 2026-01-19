package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.LSP
import fleet.util.decodeToStringUtf8
import fleet.util.encodeToByteArrayUtf8
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonElement

suspend fun withBaseProtocolFraming(
  connection: LspConnection,
  exitSignal: CompletableDeferred<Unit>?,
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
      try {
        val (key, value) = line.split(':').map { it.trim() }
        if (key == "Content-Length") {
          contentLength = value.toInt()
        }
      }
      catch (x: Throwable) {
        throw IllegalStateException("could not read header: $line", x)
      }
    }
    if (!readSomething) return null
    if (contentLength == -1) throw IllegalStateException("Content-Length header not found")
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
    append("Content-Type: application/json-rpc; charset=utf-8\r\n")
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
