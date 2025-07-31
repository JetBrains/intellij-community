// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("EXPERIMENTAL_IS_NOT_ENABLED")

package fleet.rpc.core

import fleet.util.async.Resource
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

typealias FleetTransportFactory = TransportFactory<TransportMessage>

interface TransportFactory<TMessage> {
  /*
   * [body] should be called with fully operational transport, feel free to suspend.
   * If the connection isn't possible, rethrow the cause as [TransportDisconnectedException].
   * When the underlying transport is broken, e.g. a socket is closed, both channels should be closed with [TransportDisconnectedException]
   */
  suspend fun <T> connect(
    transportStats: MutableStateFlow<TransportStats>?,
    body: suspend CoroutineScope.(Transport<TMessage>) -> T,
  ): T
}

fun <M> TransportFactory(
  factory: (MutableStateFlow<TransportStats>?) -> Resource<Transport<M>>,
): TransportFactory<M> =
  object : TransportFactory<M> {
    override suspend fun <T> connect(transportStats: MutableStateFlow<TransportStats>?, body: suspend CoroutineScope.(Transport<M>) -> T): T =
      factory(transportStats).use { body(it) }
  }

fun FleetTransportFactory(
  factory: (MutableStateFlow<TransportStats>?) -> Resource<Transport<TransportMessage>>,
): FleetTransportFactory =
  TransportFactory(factory)

/**
 * Use this function when you want to re-create your factory on each connection attempt.
 */
fun <M> dynamicTransportFactory(f: suspend () -> TransportFactory<M>): TransportFactory<M> =
  object : TransportFactory<M> {
    override suspend fun <T> connect(transportStats: MutableStateFlow<TransportStats>?, body: suspend CoroutineScope.(Transport<M>) -> T): T {
      return f().connect(transportStats, body)
    }
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
  return object : FleetTransportFactory {
    override suspend fun <T> connect(transportStats: MutableStateFlow<TransportStats>?, body: suspend CoroutineScope.(Transport<TransportMessage>) -> T): T {
      logger.debug { "Waiting for control flow to allow connection $debugToken" }
      val t = TimeSource.Monotonic.markNow()
      control.first { it == DebugConnectionState.Connect }
      val nanos = t.elapsedNow().inWholeNanoseconds
      logger.debug { "Connection $debugToken allowed, ${nanos / 1_000_000}ms spent waiting" }
      return underlying.connect(transportStats) { transport ->
        val bodyJob = async { body(transport) }
        val disconnectCommand = async { control.first { it == DebugConnectionState.Disconnect } }
        select {
          bodyJob.onAwait {
            disconnectCommand.cancel()
            it
          }
          disconnectCommand.onJoin {
            logger.debug { "Breaking connection $debugToken" }
            transport.incoming.cancel(CancellationException("TransportDisconnected",
                                                            TransportDisconnectedException("DebugDisconnect", cause = null)))
            transport.outgoing.close(TransportDisconnectedException("DebugDisconnect", cause = null))
            bodyJob.await()
          }
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