// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.util.async.DelayStrategy
import fleet.util.async.Resource
import fleet.util.async.coroutineNameAppended
import fleet.util.async.onContext
import fleet.util.async.resource
import fleet.util.async.use
import fleet.util.causeOfType
import fleet.util.logging.logger
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private object ConnectionLoop {
  val logger = logger<ConnectionLoop>()
}

sealed class ConnectionStatus<T> {
  class Connecting<T> : ConnectionStatus<T>()

  data class Connected<T>(val value: T) : ConnectionStatus<T>()
  data class TemporarilyDisconnected<T>(
    val connectionScheduledFor: Instant,
    val delayJob: Job,
    val reason: Throwable?,
  ) : ConnectionStatus<T>()
}

private val minReconnectDelay = 1.milliseconds
private val maxReconnectDelay = 30.seconds
internal val Exponential = DelayStrategy.exponential(minReconnectDelay, maxReconnectDelay)

fun <T> connectionLoop(
  transportFactory: TransportFactory,
  transportStats: MutableStateFlow<TransportStats>? = null,
  delayStrategy: DelayStrategy = Exponential,
  debugName: String? = null,
  client: (Transport) -> Resource<T>,
): Resource<StateFlow<ConnectionStatus<T>>> =
  resource { cc ->
    val status = MutableStateFlow<ConnectionStatus<T>>(ConnectionStatus.Connecting())
    val coroutineName = coroutineNameAppended(debugName ?: "unnamed connection")
    launch(coroutineName) {
      var attempt = 0
      var curDelay = delayStrategy.nextDelay(Duration.ZERO)
      while (coroutineContext.isActive) {
        val ex = try {
          transportFactory.connect(transportStats) { transport ->
            curDelay = delayStrategy.nextDelay(Duration.ZERO)
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
          ConnectionLoop.logger.info { "Reconnect by <${coroutineName.name}> attempt #$attempt in $curDelay" }
          delay(curDelay)
        }
        status.value = ConnectionStatus.TemporarilyDisconnected(
          connectionScheduledFor = Clock.System.now() + curDelay,
          delayJob = delayJob,
          reason = ex.causeOfType<TransportDisconnectedException>() ?: ex)
        delayJob.join()
        curDelay = delayStrategy.nextDelay(curDelay)
      }
    }.use {
      cc(status)
    }
  }.onContext(CoroutineName("connectionLoop $debugName"))

typealias ServiceFn<T> = suspend CoroutineScope.(T) -> Nothing

suspend fun <T> serviceConnectionLoop(
  transportFactory: suspend (ServiceFn<T>) -> Nothing,
  debugName: String? = null,
  delayStrategy: DelayStrategy = Exponential,
  service: ServiceFn<T>,
): Nothing {
  var attempt = 0
  var curDelay = delayStrategy.nextDelay(Duration.ZERO)
  while (true) {
    currentCoroutineContext().ensureActive()
    val ex = try {
      transportFactory { transport ->
        curDelay = delayStrategy.nextDelay(Duration.ZERO)
        service(transport)
      }
    }
    catch (ex: Throwable) {
      ex
    }
    val reason = ex.causeOfType<TransportDisconnectedException>() ?: ex
    ConnectionLoop.logger.debug(reason) { "Connection broken <${debugName}>" }
    attempt++
    ConnectionLoop.logger.info { "Reconnect by <${debugName}> attempt #$attempt in $curDelay" }
    delay(curDelay)
    curDelay = delayStrategy.nextDelay(curDelay)
  }
}