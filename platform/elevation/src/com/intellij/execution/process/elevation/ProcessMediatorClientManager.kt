// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.application.subscribe
import com.intellij.execution.process.elevation.settings.ElevationSettings
import com.intellij.execution.process.mediator.ProcessMediatorClient
import com.intellij.execution.process.mediator.daemon.TimeQuotaOptions
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext

class ProcessMediatorClientManager : Disposable {
  private var isDisposed = false  // synchronized on this
  private val parkedClients = mutableListOf<ProcessMediatorClient>()  // synchronized on this

  private val activeClientLazy = SynchronizedClearableLazy {
    launchDaemonAndConnectClient()
  }
  @get:JvmName("getOrCreateClient")
  private val activeClient: ProcessMediatorClient by activeClientLazy

  init {
    ElevationSettings.Listener.TOPIC.subscribe(this, object : ElevationSettings.Listener {
      override fun onDaemonQuotaOptionsChanged(oldValue: TimeQuotaOptions, newValue: TimeQuotaOptions) {
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

  private fun adjustQuota(quotaOptions: TimeQuotaOptions) {
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

    val coroutineScope = CoroutineScope(EmptyCoroutineContext)
    val daemon = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
      ProcessMediatorDaemonLauncher.launchDaemon(sudo = true)
    }, ElevationBundle.message("progress.title.starting.elevation.daemon"), true, null)
    val channel = daemon.createChannel()
    return ProcessMediatorClient(coroutineScope, channel, ElevationSettings.getInstance().quotaOptions)
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
