// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.core.TransportMessage
import fleet.util.UID
import fleet.util.channels.use
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import fleet.multiplatform.shims.ConcurrentHashMap
import fleet.rpc.EndpointKind

class ServerRequestDispatcher(private val connectionListener: ConnectionListener?) : RequestDispatcher {

  companion object {
    private val log = logger<ServerRequestDispatcher>()
  }

  private val bannedEndpoints = MutableStateFlow<Set<UID>>(emptySet())
  private val connections = ConcurrentHashMap<UID, SendChannel<TransportMessage>>()

  fun ban(route: UID) {
    bannedEndpoints.update { it + route }
  }

  fun unban(route: UID) {
    bannedEndpoints.update { it - route }
  }

  fun banned(): Flow<Set<UID>> {
    return bannedEndpoints.asStateFlow()
  }

  override suspend fun handleConnection(
    route: UID,
    endpoint: EndpointKind,
    presentableName: String?,
    send: SendChannel<TransportMessage>,
    receive: ReceiveChannel<TransportMessage>,
  ) {
    withContext(CoroutineName("handleConnection $route")) {
      val socketId = UID.random()
      log.info { "handleConnection endpoint: $endpoint, route: $route, socket id: $socketId" }
      receive.consume {
        send.use {
          bannedEndpoints.first { !it.contains(route) }
          try {
            val existing = connections.put(route, send)
            if (existing != null) {
              log.warn { "Replaced existing ${route}, will close previous socket" }
              existing.close(RuntimeException("Replaced by other connection with same uid ${route}"))
            }
            log.info { "Notify $route is connected" }
            broadcastSafely(TransportMessage.RouteOpened(route))
            connectionListener?.onConnect(endpoint, route, socketId, presentableName)
            coroutineScope {
              val connectionJob = launch(Dispatchers.Unconfined) {
                receive.consumeEach { message ->
                  when (message) {
                    is TransportMessage.Envelope -> {
                      val destination = connections[message.destination]
                      if (destination != null) {
                        kotlin.runCatching { destination.send(message) }
                          .onFailure { ex ->
                            if (log.isTraceEnabled) {
                              log.trace(ex) { "Failed to send message from $route to ${message.destination}: $message" }
                            }
                            else {
                              log.warn { "Failed to send message from $route to ${message.destination}" }
                            }
                          }
                      }
                      else {
                        val closed = TransportMessage.RouteClosed(message.destination)
                        log.trace { "Sending $closed to $route because route is not registered" }
                        send.send(closed)
                      }
                    }
                    else -> {
                      log.warn { "Good endpoints should send only TransportMessage.Envelope, but ${route} sends ${message}" }
                    }
                  }
                }
              }
              val banned = async { bannedEndpoints.first { it.contains(route) } }
              select {
                connectionJob.onJoin {
                  banned.cancelAndJoin()
                }
                banned.onJoin {
                  connectionJob.cancelAndJoin()
                }
              }
            }
          }
          finally {
            val removed = connections.remove(route, send)
            connectionListener?.onDisconnect(endpoint, route, socketId)
            if (removed) {
              log.info { "Notify $route is disconnected" }
              broadcastSafely(TransportMessage.RouteClosed(route))
            }
          }
        }
      }
    }
  }

  private suspend fun broadcastSafely(message: TransportMessage) {
    connections.forEach { (k, v) ->
      log.trace { "Broadcasting $message to ${k}" }
      //      coroutineContext.job.ensureActive()
      runCatching { v.send(message) }.onFailure { ex ->
        log.trace(ex) { "failed to broadcast $message to ${k}" }
      }
    }
  }
}
