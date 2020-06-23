// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io.jsonRpc

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.SimpleTimer
import com.intellij.util.containers.CollectionFactory
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.util.AttributeKey
import org.jetbrains.concurrency.Promise
import org.jetbrains.io.webSocket.WebSocketServerOptions
import java.util.function.Consumer
import java.util.function.Predicate

internal val CLIENT = AttributeKey.valueOf<Client>("SocketHandler.client")

class ClientManager(private val listener: ClientListener?, val exceptionHandler: ExceptionHandler, options: WebSocketServerOptions? = null) : Disposable {
  private val heartbeatTimer = SimpleTimer.getInstance().setUp({
                                                                 forEachClient(Consumer {
                                                                   if (it.channel.isActive) {
                                                                     it.sendHeartbeat()
                                                                   }
                                                                 })
  }, (options ?: WebSocketServerOptions()).heartbeatDelay.toLong())

  private val clients = CollectionFactory.createSmallMemoryFootprintSet<Client>()

  fun addClient(client: Client) {
    synchronized (clients) {
      clients.add(client)
    }
  }

  private val clientCount: Int
    get() = synchronized (clients) { clients.size }

  fun hasClients(): Boolean = clientCount > 0

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
    forEachClient(object : Consumer<Client> {
      private var first: Boolean = false

      override fun accept(client: Client) {
        try {
          val result = client.send<Pair<Client, T>>(messageId, if (first) message else message.duplicate())
          first = false
          results?.add(result!!)
        }
        catch (e: Throwable) {
          exceptionHandler.exceptionCaught(e)
        }
      }
    })
  }

  fun disconnectClient(channel: Channel, client: Client, closeChannel: Boolean): Boolean {
    synchronized (clients) {
      if (!clients.remove(client)) {
        return false
      }
    }

    try {
      channel.attr(CLIENT).set(null)

      if (closeChannel) {
        channel.close()
      }

      client.rejectAsyncResults(exceptionHandler)
    }
    finally {
      listener?.disconnected(client)
    }
    return true
  }

  private fun forEachClient(procedure: Consumer<Client>) {
    synchronized (clients) {
      for (client in clients) {
        procedure.accept(client)
      }
    }
  }

  fun findClient(predicate: Predicate<Client>): Client? {
    synchronized (clients) {
      return clients.firstOrNull { predicate.test(it) }
    }
  }
}