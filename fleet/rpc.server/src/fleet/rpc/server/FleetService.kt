// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.core.FleetTransportFactory
import fleet.rpc.core.Serialization
import fleet.rpc.core.connectionLoop
import fleet.util.UID
import fleet.util.async.use
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class FleetService private constructor(val serviceId: UID,
                                       private val job: Job) {
  private suspend fun terminate(cause: CancellationException? = null) {
    job.cancel(cause)
    job.join()
  }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.app.fleet.tests"])
  suspend fun terminate(cause: String) {
    terminate(CancellationException(cause))
  }

  companion object {
    suspend fun service(providerId: UID,
                        transportFactory: FleetTransportFactory,
                        json: () -> Serialization,
                        services: RpcServiceLocator,
                        rpcInterceptor: RpcExecutorMiddleware = RpcExecutorMiddleware,
                        rpcCallDispatcher: CoroutineDispatcher? = null,
                        body: suspend CoroutineScope.(FleetService) -> Unit) {
      coroutineScope {
        launch {
          val status = MutableStateFlow(false)
          val (serviceJob, _) = connectionLoop("RpcExecutor for service provider ${providerId}") {
            transportFactory.connect(transportStats = null) { transport ->
              status.value = true
              try {
                RpcExecutor.serve(services = services,
                                  json = json,
                                  sendChannel = transport.outgoing,
                                  receiveChannel = transport.incoming,
                                  rpcInterceptor = rpcInterceptor,
                                  rpcCallDispatcher = rpcCallDispatcher,
                                  route = providerId)
              }
              finally {
                status.value = false
              }
            }
          }
        }.use { serviceJob ->
          body(FleetService(providerId, serviceJob))
        }
      }
    }
  }
}