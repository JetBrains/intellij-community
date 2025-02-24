// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")

package fleet.rpc.core

import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlin.time.TimeSource

private val logger = logger<Transport<*>>()

class Transport<T>(
  val outgoing: SendChannel<T>,
  val incoming: ReceiveChannel<T>,
)

fun interface FleetTransportFactory {
  /*
   * [body] should be called with fully operational transport, feel free to suspend.
   * If the connection isn't possible, rethrow the cause as [TransportDisconnectedException].
   * When the underlying transport is broken, e.g. a socket is closed, both channels should be closed with [TransportDisconnectedException]
   */
  suspend fun connect(
    transportStats: MutableStateFlow<TransportStats>?,
    body: suspend CoroutineScope.(Transport<TransportMessage>) -> Unit,
  )
}

/**
 * Use this function when you want to re-create your factory on each connection attempt.
 */
fun dynamicTransportFactory(f: suspend () -> FleetTransportFactory): FleetTransportFactory =
  FleetTransportFactory { socketStats, body ->
    f().connect(socketStats, body)
  }

enum class DebugConnectionState {
  Connect,
  Disconnect
}

fun DebugConnectionState.toggle(): DebugConnectionState {
  return when (this) {
    DebugConnectionState.Connect -> DebugConnectionState.Disconnect
    DebugConnectionState.Disconnect -> DebugConnectionState.Connect
  }
}

fun FleetTransportFactory.debugDisconnect(control: StateFlow<DebugConnectionState>, debugToken: String? = null): FleetTransportFactory {
  val underlying = this
  return FleetTransportFactory { transportStats, body ->
    logger.debug { "Waiting for control flow to allow connection $debugToken" }
    val t = TimeSource.Monotonic.markNow()
    control.first { it == DebugConnectionState.Connect }
    val nanos = t.elapsedNow().inWholeNanoseconds
    logger.debug { "Connection $debugToken allowed, ${nanos / 1_000_000}ms spent waiting" }
    underlying.connect(transportStats) { transport ->
      val bodyJob = launch { body(transport) }
      val disconnectCommand = async { control.first { it == DebugConnectionState.Disconnect } }
      select<Unit> {
        bodyJob.onJoin {
          disconnectCommand.cancel()
        }
        disconnectCommand.onJoin {
          logger.debug { "Breaking connection $debugToken" }
          transport.incoming.cancel(CancellationException("TransportDisconnected",
                                                          TransportDisconnectedException("DebugDisconnect", cause = null)))
          transport.outgoing.close(TransportDisconnectedException("DebugDisconnect", cause = null))
        }
      }
    }
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransportDisconnectedException(reason: String?, cause: Throwable?)
  : RuntimeException(reason, cause), CopyableThrowable<TransportDisconnectedException> {

  override fun createCopy(): TransportDisconnectedException {
    return TransportDisconnectedException(message, this)
  }
}