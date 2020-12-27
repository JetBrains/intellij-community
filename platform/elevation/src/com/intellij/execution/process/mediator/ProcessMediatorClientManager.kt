// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.intellij.application.subscribe
import com.intellij.execution.process.elevation.ElevationLogger
import com.intellij.execution.process.elevation.settings.ElevationSettings
import com.intellij.execution.process.mediator.client.ProcessMediatorClient
import com.intellij.execution.process.mediator.daemon.DaemonClientCredentials
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemon
import com.intellij.execution.process.mediator.daemon.ProcessMediatorServerDaemon
import com.intellij.execution.process.mediator.daemon.QuotaOptions
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.SynchronizedClearableLazy
import io.grpc.ManagedChannel
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext

class ProcessMediatorClientManager(private val launchDaemon: () -> ProcessMediatorDaemon,
                                   private val createClient: (ManagedChannel) -> ProcessMediatorClient) : Disposable {
  private var isDisposed = false  // synchronized on this
  private val parkedClients = mutableListOf<ProcessMediatorClient>()  // synchronized on this

  private val activeClientLazy = SynchronizedClearableLazy {
    launchDaemonAndConnectClient()
  }

  @get:JvmName("getOrCreateClient")
  private val activeClient: ProcessMediatorClient by activeClientLazy

  init {
    ElevationSettings.Listener.TOPIC.subscribe(this, object : ElevationSettings.Listener {
      override fun onDaemonQuotaOptionsChanged(oldValue: QuotaOptions, newValue: QuotaOptions) {
        adjustQuota(newValue)
      }
    })
  }

  fun launchDaemonAndConnectClientIfNeeded() = activeClient
  private fun getActiveClientOrNull() = activeClientLazy.valueIfInitialized

  fun parkClient(expectedClient: ProcessMediatorClient) {
    synchronized(this) {
      if (activeClientLazy.compareAndDrop(expectedClient)) {
        parkedClients.add(expectedClient)
      }
    }
  }

  private fun adjustQuota(quotaOptions: QuotaOptions) {
    synchronized(this) {
      for (client in parkedClients) {
        try {
          client.adjustQuotaBlocking(quotaOptions)
        }
        catch (e: Exception) {
          ElevationLogger.LOG.warn("Unable to adjust quota for client $client")
        }
      }
      getActiveClientOrNull()?.adjustQuotaBlocking(quotaOptions)
    }
  }

  private fun launchDaemonAndConnectClient(): ProcessMediatorClient = synchronized(this) {
    check(!isDisposed) { "Already disposed" }

    val debug = false
    val daemon = if (debug) createInProcessDaemonForDebugging(CoroutineScope(EmptyCoroutineContext)) else launchDaemon()
    val channel = daemon.createChannel()
    return createClient(channel)
  }

  private fun createInProcessDaemonForDebugging(coroutineScope: CoroutineScope): ProcessMediatorDaemon {
    val bindName = "testing${parkedClients.size}"
    val credentials = DaemonClientCredentials.generate()
    return object : ProcessMediatorServerDaemon(coroutineScope, InProcessServerBuilder.forName(bindName).directExecutor(), credentials) {
      override fun createChannel(): ManagedChannel {
        return InProcessChannelBuilder.forName(bindName)
          .intercept(MetadataUtils.newAttachHeadersInterceptor(credentials.asMetadata()))
          .directExecutor().build()
      }
    }
  }

  override fun dispose() {
    synchronized(this) {
      if (isDisposed) return
      isDisposed = true
      for (client in parkedClients) {
        client.close()
      }
      activeClientLazy.drop()?.close()
    }
  }
}
