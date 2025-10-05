package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.LSP
import fleet.util.decodeToStringUtf8
import fleet.util.encodeToByteArrayUtf8
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    val readJob = launch {
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
    val writeJob = launch {
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

private suspend fun ByteReadChannel.readFrame(): JsonElement? {
  var contentLength = -1
  var readSomething = false
  val buf = try {
    while (!isClosedForRead) {
      val line = readUTF8Line()
      if (line.isNullOrEmpty()) break
      readSomething = true
      val (key, value) = line.split(':').map { it.trim() }
      if (key == "Content-Length") {
        contentLength = value.toInt()
      }
    }
    if (!readSomething) return null
    if (contentLength == -1) throw IllegalStateException("Content-Length header not found")
    readByteArray(contentLength)
  } catch (e: Exception) {
    when (e) {
      is IOException -> return null
      else -> throw e
    }
  }
  return LSP.json.decodeFromString(JsonElement.serializer(), buf.decodeToStringUtf8())
}

/**
 * @return Boolean indicating whether the frame was successfully written (`true`) or the channel was closed (`false`).
 */
private suspend fun ByteWriteChannel.writeFrame(jsonElement: JsonElement): Boolean {
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