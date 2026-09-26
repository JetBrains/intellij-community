package fleet.kernel.rebase.test

import fleet.rpc.EndpointKind
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.client.ClientId
import fleet.rpc.client.fleetClient
import fleet.rpc.client.proxy
import fleet.rpc.core.DebugConnectionState
import fleet.rpc.core.FleetTransportFactory
import fleet.rpc.core.InstanceId
import fleet.rpc.core.ProtocolVersion
import fleet.rpc.core.Transport
import fleet.rpc.core.TransportMessage
import fleet.rpc.core.TransportStats
import fleet.rpc.core.debugDisconnect
import fleet.rpc.server.FleetService
import fleet.rpc.server.MapServiceLocator
import fleet.rpc.server.ServerRequestDispatcher
import fleet.rpc.server.ServiceImplementation
import fleet.util.UID
import fleet.util.async.use
import fleet.util.channels.channels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * A transport backed by a pair of in-memory channels, the other end of which is handed over to [connector].
 */
private class TestTransportFactory(
  private val connector: (CoroutineScope, SendChannel<TransportMessage>, ReceiveChannel<TransportMessage>) -> Unit,
) : FleetTransportFactory {
  override suspend fun <T> connect(
    transportStats: MutableStateFlow<TransportStats>?,
    body: suspend CoroutineScope.(Transport) -> T,
  ): T =
    coroutineScope {
      val (transportSend, connectorReceive) = channels<TransportMessage>()
      val (connectorSend, transportReceive) = channels<TransportMessage>()
      launch {
        connector(this, connectorSend, connectorReceive)
      }.use {
        body(Transport(transportSend, transportReceive))
      }
    }
}

/**
 * A [FleetTransportFactory] which may be broken and restored at will, see [connect] and [disconnect].
 */
internal class ReconnectTestTransportFactory(
  private val debugToken: String,
  private val connector: (CoroutineScope, SendChannel<TransportMessage>, ReceiveChannel<TransportMessage>) -> Unit,
) : FleetTransportFactory {
  private val state = MutableStateFlow(DebugConnectionState.Disconnect)

  override suspend fun <T> connect(
    transportStats: MutableStateFlow<TransportStats>?,
    body: suspend CoroutineScope.(Transport) -> T,
  ): T =
    TestTransportFactory(connector)
      .debugDisconnect(state.asStateFlow(), debugToken)
      .connect(transportStats, body)

  fun connect() {
    state.value = DebugConnectionState.Connect
  }

  fun disconnect() {
    state.value = DebugConnectionState.Disconnect
  }
}

internal class ReconnectTestContext<A : RemoteApi<Unit>>(
  val clientTransport: ReconnectTestTransportFactory,
  val serviceTransport: ReconnectTestTransportFactory,
  val remoteApi: A,
)

internal suspend fun randomDelay(upToMs: Long) {
  delay((Random.nextDouble() * upToMs).toLong())
}

/**
 * Serves [implementation] over an rpc connection whose both ends may be broken at will,
 * and offers a client-side proxy of it to the [body].
 */
internal suspend fun <A : RemoteApi<Unit>> reconnectTest(
  remoteApiDescriptor: RemoteApiDescriptor<A>,
  implementation: A,
  body: suspend ReconnectTestContext<A>.() -> Unit,
) {
  coroutineScope {
    val serviceRoute = UID.random()
    val clientRoute = UID.random()
    val instanceId = InstanceId(remoteApiDescriptor.getApiFqn())
    val dispatcher = ServerRequestDispatcher(connectionListener = null)
    val serviceTransport = ReconnectTestTransportFactory(serviceRoute.id) { scope, send, receive ->
      scope.launch {
        dispatcher.handleConnection(route = serviceRoute,
                                    endpoint = EndpointKind.Provider,
                                    protocolVersion = ProtocolVersion.current,
                                    send = send,
                                    receive = receive)
      }
    }
    val clientTransport = ReconnectTestTransportFactory(clientRoute.id) { scope, send, receive ->
      scope.launch {
        dispatcher.handleConnection(route = clientRoute,
                                    endpoint = EndpointKind.Client,
                                    protocolVersion = ProtocolVersion.current,
                                    send = send,
                                    receive = receive)
      }
    }
    FleetService.service(providerId = serviceRoute,
                         transportFactory = serviceTransport,
                         services = MapServiceLocator(mapOf(instanceId to ServiceImplementation(remoteApiDescriptor,
                                                                                                implementation,
                                                                                                null)))) {
      // the transport is broken on purpose all the time, rpc failures are expected here:
      fleetClient(clientId = ClientId(clientRoute),
                  transportFactory = clientTransport,
                  abortOnError = false).use { client ->
        ReconnectTestContext(clientTransport = clientTransport,
                             serviceTransport = serviceTransport,
                             remoteApi = client.proxy(remoteApiDescriptor, serviceRoute, instanceId)).body()
      }
    }
  }
}
