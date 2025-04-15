// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.core.FleetTransportFactory
import fleet.rpc.core.connectionLoop
import fleet.util.UID
import fleet.util.async.use
import kotlinx.coroutines.*

class FleetService private constructor(
  val serviceId: UID,
) {
  companion object {
    suspend fun <T> service(
      providerId: UID,
      transportFactory: FleetTransportFactory,
      services: RpcServiceLocator,
      rpcInterceptor: RpcExecutorMiddleware = RpcExecutorMiddleware,
      rpcCallDispatcher: CoroutineDispatcher? = null,
      body: suspend CoroutineScope.(FleetService) -> T,
    ): T =
      coroutineScope {
        launch {
          connectionLoop(debugName = "RpcExecutor for service provider ${providerId}") { cc ->
            transportFactory.connect(transportStats = null) { transport ->
              cc(
                RpcExecutor.serve(
                  services = services,
                  sendChannel = transport.outgoing,
                  receiveChannel = transport.incoming,
                  rpcInterceptor = rpcInterceptor,
                  rpcCallDispatcher = rpcCallDispatcher,
                  route = providerId,
                )
              )
            }
          }.use {
            awaitCancellation()
          }
        }.use {
          body(FleetService(serviceId = providerId))
        }
      }
  }
}
