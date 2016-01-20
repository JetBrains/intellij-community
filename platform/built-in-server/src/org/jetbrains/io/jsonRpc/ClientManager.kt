package org.jetbrains.io.jsonRpc

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.SimpleTimer
import gnu.trove.THashSet
import gnu.trove.TObjectProcedure
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.util.AttributeKey
import org.jetbrains.concurrency.Promise
import org.jetbrains.io.webSocket.WebSocketServerOptions

val CLIENT = AttributeKey.valueOf<Client>("SocketHandler.client")

class ClientManager(private val listener: ClientListener?, val exceptionHandler: ExceptionHandler, options: WebSocketServerOptions? = null) : Disposable {
  private val heartbeatTimer = SimpleTimer.getInstance().setUp(Runnable {
    synchronized (clients) {
      if (clients.isEmpty) {
        return@Runnable
      }

      clients.forEach { client ->
        if (client.channel.isActive) {
          client.sendHeartbeat()
        }
        true
      }
    }
  }, (options ?: WebSocketServerOptions()).heartbeatDelay.toLong())

  private val clients = THashSet<Client>()

  fun addClient(client: Client) {
    synchronized (clients) {
      clients.add(client)
    }
  }

  val clientCount: Int
    get() = synchronized (clients) { clients.size }

  fun hasClients() = clientCount > 0

  override fun dispose() {
    try {
      heartbeatTimer.cancel()
    }
    finally {
      synchronized (clients) {
        clients.clear()
      }
    }
  }

  fun <T> send(messageId: Int, message: ByteBuf, results: MutableList<Promise<Pair<Client, T>>>? = null) {
    forEachClient(object : TObjectProcedure<Client> {
      private var first: Boolean = false

      override fun execute(client: Client): Boolean {
        try {
          val result = client.send<Pair<Client, T>>(messageId, if (first) message else message.duplicate())
          first = false
          results?.add(result!!)
        }
        catch (e: Throwable) {
          exceptionHandler.exceptionCaught(e)
        }
        return true
      }
    })
  }

  fun disconnectClient(context: ChannelHandlerContext, client: Client, closeChannel: Boolean): Boolean {
    synchronized (clients) {
      if (!clients.remove(client)) {
        return false
      }
    }

    try {
      context.attr(CLIENT).remove()

      if (closeChannel) {
        context.channel().close()
      }

      client.rejectAsyncResults(exceptionHandler)
    }
    finally {
      listener?.disconnected(client)
    }
    return true
  }

  fun forEachClient(procedure: TObjectProcedure<Client>) {
    synchronized (clients) {
      if (clients.isEmpty) {
        return
      }

      clients.forEach(procedure)
    }
  }
}