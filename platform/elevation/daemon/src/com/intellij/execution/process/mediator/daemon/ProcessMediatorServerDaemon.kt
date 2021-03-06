// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.grpc.CredentialsAuthServerInterceptor
import com.intellij.execution.process.mediator.grpc.ExceptionAsStatus
import com.intellij.execution.process.mediator.rpc.DaemonGrpcKt
import com.intellij.execution.process.mediator.rpc.buildFrom
import com.intellij.execution.process.mediator.rpc.toQuotaOptions
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.Closeable
import com.intellij.execution.process.mediator.rpc.QuotaOptions as QuotaOptionsMessage
import com.intellij.execution.process.mediator.rpc.QuotaState as QuotaStateMessage

class ProcessMediatorServerDaemon(coroutineScope: CoroutineScope,
                                  builder: ServerBuilder<*>,
                                  credentials: DaemonClientCredentials) : Closeable {
  private val quotaManager = TimeQuotaManager(coroutineScope)
  private val processManager = ProcessManager()

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

    quotaManager.asJob().invokeOnCompletion {
      coroutineScope.launch {
        requestShutdown()
      }
    }
  }

  override fun close() {
    requestShutdown()
    blockUntilShutdown()
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

  fun blockUntilShutdown() {
    server.awaitTermination()
    System.err.println("Server terminated")
  }

  inner class DaemonService : DaemonGrpcKt.DaemonCoroutineImplBase() {
    override suspend fun adjustQuota(request: QuotaOptionsMessage): Empty {
      ExceptionAsStatus.wrap {
        val newOptions = request.toQuotaOptions()
        quotaManager.adjustQuota(newOptions)
      }
      return Empty.getDefaultInstance()
    }

    override fun listenQuotaStateUpdates(request: Empty): Flow<QuotaStateMessage> {
      return quotaManager.stateUpdateFlow.map {
        QuotaStateMessage.newBuilder().buildFrom(it)
      }
    }

    override suspend fun shutdown(request: Empty): Empty {
      ExceptionAsStatus.wrap {
        requestShutdown()
      }
      return Empty.getDefaultInstance()
    }
  }
}
