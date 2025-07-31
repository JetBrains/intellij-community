// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.util.async.*
import fleet.util.causeOfType
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock

private object ConnectionLoop {
  val logger = logger<ConnectionLoop>()
}

sealed class ConnectionStatus<T> {
  class Connecting<T> : ConnectionStatus<T>()

  data class Connected<T>(val value: T) : ConnectionStatus<T>()
  data class TemporarilyDisconnected<T>(val connectionScheduledFor: Long, val delayJob: Job, val reason: Throwable?) : ConnectionStatus<T>()
}

private const val minReconnectDelay = 1L
private const val maxReconnectDelay = 30_000L
internal val Exponential = DelayStrategy.exponential(minReconnectDelay, maxReconnectDelay)

fun <T, M> connectionLoop(
  transportFactory: TransportFactory<M>,
  transportStats: MutableStateFlow<TransportStats>? = null,
  delayStrategy: DelayStrategy = Exponential,
  debugName: String? = null,
  client: (Transport<M>) -> Resource<T>,
): Resource<StateFlow<ConnectionStatus<T>>> =
  resource { cc ->
    val status = MutableStateFlow<ConnectionStatus<T>>(ConnectionStatus.Connecting())
    val coroutineName = coroutineNameAppended(debugName ?: "unnamed connection")
    launch(coroutineName) {
      var attempt = 0
      var curDelayMs = delayStrategy.nextDelay(0)
      while (coroutineContext.isActive) {
        val ex = try {
          transportFactory.connect(transportStats) { transport ->
            curDelayMs = delayStrategy.nextDelay(0)
            client(transport).use { value ->
              status.value = ConnectionStatus.Connected(value)
              awaitCancellation()
            }
          }
        }
        catch (ex: Throwable) {
          coroutineContext.ensureActive()
          ex
        }
        attempt++
        val delayJob = launch {
          ConnectionLoop.logger.info { "Reconnect by <${coroutineName.name}> attempt #$attempt in ${curDelayMs}ms" }
          delay(curDelayMs)
        }
        status.value = ConnectionStatus.TemporarilyDisconnected(
          connectionScheduledFor = Clock.System.now().toEpochMilliseconds() + curDelayMs,
          delayJob = delayJob,
          reason = ex.causeOfType<TransportDisconnectedException>() ?: ex)
        delayJob.join()
        curDelayMs = delayStrategy.nextDelay(curDelayMs)
      }
    }.use {
      cc(status)
    }
  }.onContext(CoroutineName("connectionLoop $debugName"))

suspend fun <M> serviceConnectionLoop(
  transportFactory: TransportFactory<M>,
  debugName: String? = null,
  delayStrategy: DelayStrategy = Exponential,
  service: suspend CoroutineScope.(Transport<M>) -> Nothing,
): Nothing {
  var attempt = 0
  var curDelayMs = delayStrategy.nextDelay(0)
  while (true) {
    currentCoroutineContext().ensureActive()
    val ex = try {
      transportFactory.connect(null) { transport ->
        curDelayMs = delayStrategy.nextDelay(0)
        service(transport)
      }
    }
    catch (ex: Throwable) {
      ex
    }
    val reason = ex.causeOfType<TransportDisconnectedException>() ?: ex
    ConnectionLoop.logger.debug(reason) { "Connection broken <${debugName}>" }
    attempt++
    ConnectionLoop.logger.info { "Reconnect by <${debugName}> attempt #$attempt in ${curDelayMs}ms" }
    delay(curDelayMs)
    curDelayMs = delayStrategy.nextDelay(curDelayMs)
  }
}