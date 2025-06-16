// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import fleet.util.async.*
import fleet.util.causeOfType
import fleet.util.causes
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

fun <T> connectionLoop(
  delayStrategy: DelayStrategy = Exponential,
  debugName: String? = null,
  connection: suspend CoroutineScope.(suspend (T) -> Unit) -> Unit,
): Resource<StateFlow<ConnectionStatus<T>>> =
  resource { cc ->
    val status = MutableStateFlow<ConnectionStatus<T>>(ConnectionStatus.Connecting())
    val coroutineName = coroutineNameAppended(debugName ?: "unnamed connection")
    launch(coroutineName) {
      var attempt = 0
      var curDelayMs = delayStrategy.nextDelay(0)
      while (coroutineContext.isActive) {
        val ex = try {
          coroutineScope {
            connection { value ->
              curDelayMs = delayStrategy.nextDelay(0)
              status.value = ConnectionStatus.Connected(value)
              awaitCancellation()
            }
          }
          error("unreachable")
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
          delayJob = Job(),
          reason = ex.causeOfType<TransportDisconnectedException>() ?: ex)
        delayJob.join()
        curDelayMs = delayStrategy.nextDelay(curDelayMs)
      }
    }.use {
      cc(status)
    }
  }.onContext(CoroutineName("connectionLoop"))

@Deprecated("use the new one", replaceWith = ReplaceWith("connectionLoop"))
fun <T> CoroutineScope.connectionLoopOld(
  name: String,
  delayStrategy: DelayStrategy = Exponential,
  body: suspend CoroutineScope.() -> T,
): Pair<Job, StateFlow<ConnectionStatus<T>>> {
  val status = MutableStateFlow<ConnectionStatus<T>>(ConnectionStatus.Connecting())
  val job = launch(coroutineNameAppended(name)) {
    val connectionJobName = coroutineContext[CoroutineName]?.name ?: error("Guaranteed by coroutineNameAppended above")
    var curDelayMs = delayStrategy.nextDelay(0)
    var attempt = 0

    while (isActive) {
      val reason = try {
        withContext(coroutineNameAppended("Connection")) {
          status.value = ConnectionStatus.Connected(body())
          curDelayMs = delayStrategy.nextDelay(0)
          attempt = 0
          null
        }
      }
      catch (cause: Throwable) {
        cause.causeOfType<TransportDisconnectedException>() ?: cause.takeIf { it !is CancellationException }
      }

      if (reason != null) {
        ConnectionLoop.logger.info(reason.takeIf { ConnectionLoop.logger.isDebugEnabled }) {
          "Connection by <$connectionJobName> lost. Cause=${reason.causes().joinToString { it.message ?: it.toString() }}\n" +
          "Consider increasing logging level to DEBUG for ${ConnectionLoop::class.qualifiedName}"
        }
      }

      if (!isActive) break

      val delayJob =
        launch(coroutineNameAppended("Disconnected, awaiting for attempt to connect")) {
          attempt++
          ConnectionLoop.logger.info { "Reconnect by <$connectionJobName> attempt #$attempt in ${curDelayMs}ms" }
          delay(curDelayMs)
        }
      delayJob.invokeOnCompletion { e ->
        if (e != null) {
          ConnectionLoop.logger.info { "Delay for <$connectionJobName>(attempt #$attempt) was canceled for reason: ${e.message}" }
        }
      }
      status.value = ConnectionStatus.TemporarilyDisconnected(Clock.System.now().toEpochMilliseconds() + curDelayMs, delayJob, reason)

      delayJob.join()
      curDelayMs = delayStrategy.nextDelay(curDelayMs)
    }
  }
  return job to status.asStateFlow()
}