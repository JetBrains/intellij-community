package com.jetbrains.lsp.implementation

import fleet.util.logging.logger
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.*

suspend fun tcpServer(config: TcpConnectionConfig.Server, server: suspend CoroutineScope.(LspConnection) -> Unit) {
  SelectorManager(Dispatchers.IO).use { selectorManager ->
    aSocket(selectorManager).tcp().bind(port = config.port).use { serverSocket ->
      LOG.info("Server is listening on port ${serverSocket.localAddress}")

      supervisorScope {
        var hadClient = false
        fun shouldAccept() = !hadClient || config.isMulticlient

        while (shouldAccept()) {
          val client = serverSocket.accept()
          val clientAddress = client.remoteAddress
          hadClient = true
          LOG.info("A new client connected ${clientAddress}")
          launch(start = CoroutineStart.ATOMIC) {
            try {
              client.use { clientSocket ->
                coroutineScope { server(KtorSocketConnection(clientSocket)) }
              }
            }
            finally {
              LOG.info("Client disconnected ${clientAddress}")
            }
          }
        }
      }
    }
  }
}


suspend fun tcpClient(config: TcpConnectionConfig.Client, body: suspend CoroutineScope.(LspConnection) -> Unit) {
  SelectorManager(Dispatchers.IO).use { selectorManager ->
    try {
      aSocket(selectorManager).tcp().connect("localhost", config.port).use { server ->
        LOG.info("Client is connected to server ${server.remoteAddress}")
        coroutineScope { body(KtorSocketConnection(server)) }
      }
    }
    finally {
      LOG.info("Client disconnected from the server")
    }
  }
}


suspend fun tcpConnection(config: TcpConnectionConfig, body: suspend CoroutineScope.(LspConnection) -> Unit) {
  when (config) {
    is TcpConnectionConfig.Client -> tcpClient(config, body)
    is TcpConnectionConfig.Server -> tcpServer(config, body)
  }
}

sealed interface TcpConnectionConfig {
  val port: Int

  val isMulticlient: Boolean

  data class Client(override val port: Int) : TcpConnectionConfig {
    override val isMulticlient: Boolean = false
  }

  data class Server(override val port: Int, override val isMulticlient: Boolean) : TcpConnectionConfig
}

class KtorSocketConnection(private val socket: Socket) : LspConnection {
  override val input: ByteReadChannel = socket.openReadChannel()
  override val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

  override fun close() {
    socket.close()
  }

  override fun isAlive(): Boolean {
    return !socket.isClosed
  }
}


private val LOG = logger<LspClient>()
