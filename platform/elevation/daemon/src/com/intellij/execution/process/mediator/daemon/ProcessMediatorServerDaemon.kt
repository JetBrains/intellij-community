// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.grpc.CredentialsAuthServerInterceptor
import com.intellij.execution.process.mediator.grpc.ExceptionAsStatus
import com.intellij.execution.process.mediator.rpc.AdjustQuotaRequest
import com.intellij.execution.process.mediator.rpc.DaemonGrpcKt
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

@Suppress("LeakingThis")
open class ProcessMediatorServerDaemon(coroutineScope: CoroutineScope,
                                       builder: ServerBuilder<*>,
                                       credentials: DaemonClientCredentials) : ProcessMediatorDaemon,
                                                                               CoroutineScope by coroutineScope {

  private val quotaManager = TimeQuotaManager(this)
  private val processManager = ProcessManager(this + quotaManager.asJob())

  private val server: Server

  val port get() = server.port

  init {
    this.server = builder
      .intercept(CredentialsAuthServerInterceptor(credentials))
      .addService(ProcessManagerServerService.createServiceDefinition(processManager, quotaManager))
      .addService(DaemonService())
      .build()
      .start()
      .also {
        System.err.println("Started server on port ${it.port}")
      }
  }

  override fun stop() {
    requestShutdown()
  }

  fun requestShutdown(): Unit = synchronized(server) {
    if (!server.isShutdown) {
      System.err.println("Server shutdown requested")
      quotaManager.use {  // to close it
        processManager.use {
          server.shutdown()
        }
      }
    }
    else {
      System.err.println("Server shutdown requested, but it's already been shut down")
    }
  }

  override fun blockUntilShutdown() {
    server.awaitTermination()
  }

  inner class DaemonService : DaemonGrpcKt.DaemonCoroutineImplBase() {
    override suspend fun adjustQuota(request: AdjustQuotaRequest): Empty {
      ExceptionAsStatus.wrap {
        val quotaOptions = QuotaOptions(timeLimitMs = request.timeLimitMs,
                                        isRefreshable = request.isRefreshable)
        quotaManager.adjustQuota(quotaOptions)
      }
      return Empty.getDefaultInstance()
    }

    override suspend fun shutdown(request: Empty): Empty {
      ExceptionAsStatus.wrap {
        requestShutdown()
      }
      return Empty.getDefaultInstance()
    }
  }
}
