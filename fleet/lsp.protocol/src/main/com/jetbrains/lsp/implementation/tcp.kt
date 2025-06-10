package com.jetbrains.lsp.implementation

import fleet.util.logging.logger
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

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


/**
 * VSC opens a **server** socket for LSP to connect to it.
 */
suspend fun tcpClient(port: Int, body: suspend CoroutineScope.(InputStream, OutputStream) -> Unit) {
  val socket = runInterruptible(Dispatchers.IO) {
    Socket("localhost", port)
  }
  socket.use {
    coroutineScope {
      body(socket.getInputStream(), socket.getOutputStream())
    }
  }
}


suspend fun tcpConnection(clientMode: Boolean, port: Int, body: suspend CoroutineScope.(InputStream, OutputStream) -> Unit) {
  when {
    clientMode -> {
      tcpClient(port, body)
    }

    else -> {
      tcpServer(port, body)
    }
  }
}


private val LOG = logger<LspClient>()
