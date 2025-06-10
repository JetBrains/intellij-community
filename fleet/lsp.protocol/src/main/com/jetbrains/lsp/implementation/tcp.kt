package com.jetbrains.lsp.implementation

import fleet.util.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import kotlin.io.use

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
