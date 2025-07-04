package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonElement
import java.io.ByteArrayOutputStream

private suspend fun ByteReadChannel.readLine(): String {
    val buffer = ByteArrayOutputStream()
    var prevChar: Int? = null
    while (true) {
        val currentChar = this.readByte().toInt()
        if (currentChar == -1 || (prevChar == '\r'.code && currentChar == '\n'.code)) {
            break
        }
        if (prevChar != null) {
            buffer.write(prevChar)
        }
        prevChar = currentChar
    }
    return buffer.toString(Charsets.UTF_8).trim()
}

private suspend fun ByteReadChannel.readFrame(): JsonElement? {
    var contentLength = -1
    var readSomething = false
    val buf = try {
      while (!isClosedForRead) {
          val line = this.readLine()
          if (line.isEmpty()) break
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
    return LSP.json.decodeFromString(JsonElement.serializer(), String(buf, Charsets.UTF_8))
}

/**
 * @return Boolean indicating whether the frame was successfully written (`true`) or the channel was closed (`false`).
 */
private suspend fun ByteWriteChannel.writeFrame(jsonElement: JsonElement): Boolean {
    val str = LSP.json.encodeToString(JsonElement.serializer(), jsonElement)
    val frameStr = buildString {
        // protocol requires string length in bytes
        val contentLengthInBytes = str.toByteArray(Charsets.UTF_8).size
        append("Content-Length: $contentLengthInBytes\r\n")
        append("Content-Type: application/json-rpc; charset=utf-8\r\n")
        append("\r\n")
        append(str)
    }
  try {
    if (isClosedForWrite) return false
    writeByteArray(frameStr.toByteArray(Charsets.UTF_8))
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

fun main() {
    val handler = lspHandlers<LspHandlerContext> {
        request(Initialize) { initParams ->
            InitializeResult(
                capabilities = ServerCapabilities(
                    textDocumentSync = TextDocumentSyncKind.Incremental,
                ),
                serverInfo = InitializeResult.ServerInfo(
                    name = "IntelliJ Analyzer",
                    version = "1.0"
                ),
            )
        }
        notification(DocumentSync.DidOpen) { didOpen ->
            println("didOpen: $didOpen")
        }
        notification(DocumentSync.DidChange) { didChange ->
            println("didChange: $didChange")
        }
    }
    runBlocking(Dispatchers.Default) {
        tcpServer(TcpConnectionConfig.Server("127.0.0.1", 9999, isMultiClient = true)) { connection ->
            withBaseProtocolFraming(connection, exitSignal = null) { incoming, outgoing ->
                withLsp(incoming, outgoing, handler, ::LspHandlerContext) { lsp ->
                    awaitCancellation()
                }
            }
        }
    }
}