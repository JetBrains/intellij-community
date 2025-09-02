package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.Initialize
import com.jetbrains.lsp.protocol.InitializeResult
import com.jetbrains.lsp.protocol.*
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.JsonElement
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket

suspend fun tcpServer(port: Int = 0, server: suspend CoroutineScope.(InputStream, OutputStream) -> Unit) {
    ServerSocket(port).use { serverSocket ->
        LOG.info("Server is listening on port ${serverSocket.localPort}")
        supervisorScope {
            while (true) {
                val clientSocket = runInterruptible(Dispatchers.IO) {
                    serverSocket.accept()
                }
                LOG.info("A new client connected ${clientSocket.inetAddress}:${clientSocket.port}")
                launch(start = CoroutineStart.ATOMIC) {
                    clientSocket.use {
                        val input = clientSocket.getInputStream()
                        val output = clientSocket.getOutputStream()
                        coroutineScope {
                            server(input, output)
                        }
                    }
                    LOG.info("Client disconnected ${clientSocket.inetAddress}:${clientSocket.port}")
                }
            }
        }
    }
}

private val LOG = logger<LspClient>()

private fun InputStream.readLine(): String {
    val buffer = ByteArrayOutputStream()
    var prevChar: Int? = null
    while (true) {
        val currentChar = this.read()
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

private fun InputStream.readFrame(): JsonElement? {
    var contentLength = -1
    var readSomething = false
    while (true) {
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
    val buf = readNBytes(contentLength)
    return LSP.json.decodeFromString(JsonElement.serializer(), String(buf, Charsets.UTF_8))
}

private fun OutputStream.writeFrame(jsonElement: JsonElement) {
    val str = LSP.json.encodeToString(JsonElement.serializer(), jsonElement)
    val frameStr = buildString {
        // protocol requires string length in bytes
        val contentLengthInBytes = str.toByteArray(Charsets.UTF_8).size
        append("Content-Length: $contentLengthInBytes\r\n")
        append("Content-Type: application/json-rpc; charset=utf-8\r\n")
        append("\r\n")
        append(str)
    }
    write(frameStr.toByteArray(Charsets.UTF_8))
    flush()
}

suspend fun withBaseProtocolFraming(
    reader: InputStream,
    writer: OutputStream,
    body: suspend CoroutineScope.(
        incoming: ReceiveChannel<JsonElement>,
        outgoing: SendChannel<JsonElement>,
    ) -> Unit,
) {
    coroutineScope {
        val (incomingSender, incomingReceiver) = channels<JsonElement>()
        val (outgoingSender, outgoingReceiver) = channels<JsonElement>(Channel.UNLIMITED)
        launch {
            launch {
                incomingSender.use {
                    while (true) {
                        val frame = runInterruptible(Dispatchers.IO) {
                            reader.readFrame()
                        }
                        //            println("received frame $frame")
                        if (frame == null) {
                            incomingSender.close()
                            break
                        }
                        incomingSender.send(frame)
                    }
                }
            }
            launch {
                outgoingReceiver.consumeEach { frame ->
                    //          println("sending frame $frame")
                    runInterruptible(Dispatchers.IO) {
                        writer.writeFrame(frame)
                    }
                }
            }
        }.use {
            body(incomingReceiver, outgoingSender)
        }
    }
}

fun main() {
    val handler = lspHandlers {
        request(Initialize) { initParams ->
            InitializeResult(
                capabilities = ServerCapabilities(
                    textDocumentSync = TextDocumentSync(TextDocumentSyncKind.Incremental),
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
        tcpServer(9999) { input, output ->
            withBaseProtocolFraming(input, output) { incoming, outgoing ->
                withLsp(incoming, outgoing, handler) { lsp ->
                    awaitCancellation()
                }
            }
        }
    }
}