package com.jetbrains.lsp.implementation

import fleet.util.logging.logger
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

suspend fun tcpServer(port: Int = 0, server: suspend CoroutineScope.(LspConnection) -> Unit) {
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
            val connection = LspSocketConnection(clientSocket)
            coroutineScope {
              server(connection)
            }
          }
          LOG.info("Client disconnected ${clientSocket.inetAddress}:${clientSocket.port}")
        }
      }
    }
  }
}


/**
 * VSC opens a **server** socket for LSP to connect to it.
 */
suspend fun tcpClient(port: Int, body: suspend CoroutineScope.(LspConnection) -> Unit) {
  val socket = runInterruptible(Dispatchers.IO) {
    Socket("localhost", port)
  }
  socket.use {
    coroutineScope {
      val connection = LspSocketConnection(socket)
      body(connection)
    }
  }
}


suspend fun tcpConnection(clientMode: Boolean, port: Int, body: suspend CoroutineScope.(LspConnection) -> Unit) {
  when {
    clientMode -> {
      tcpClient(port, body)
    }

    else -> {
      tcpServer(port, body)
    }
  }
}

class LspSocketConnection(private val socket: Socket) : LspConnection {
  override val inputStream: InputStream get() = socket.getInputStream()
  override val outputStream: OutputStream get() = socket.getOutputStream()

  override fun disconnect() {
    socket.close()
  }

  override fun isAlive(): Boolean {
    return !socket.isClosed
  }
}


private val LOG = logger<LspClient>()
