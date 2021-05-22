// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.launcher

import com.intellij.execution.process.mediator.daemon.QuotaOptions
import com.intellij.execution.process.mediator.daemon.QuotaState
import com.intellij.openapi.Disposable
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.io.MultiCloseable
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

class ProcessMediatorConnectionManager(private val connectionProvider: () -> ProcessMediatorConnection) : Disposable {
  private var isDisposed = false  // synchronized on this
  private val parkedConnections = mutableListOf<ProcessMediatorConnection>()  // synchronized on this

  private val activeConnectionLazy = SynchronizedClearableLazy {
    launchDaemonAndConnect().apply connection@{
      client.stateUpdateFlow
        .onEach {
          if (it is QuotaState.Expired) {
            parkConnection(this@connection)
          }
        }
        .onCompletion {
          removeParkedConnection(this@connection)
        }
        .launchIn(client.coroutineScope)
    }
  }

  @get:JvmName("getOrCreateConnection")
  private val activeConnection: ProcessMediatorConnection by activeConnectionLazy

  fun launchDaemonAndConnectIfNeeded() = activeConnection
  private fun getActiveConnectionOrNull() = activeConnectionLazy.valueIfInitialized

  fun parkConnection(expectedConnection: ProcessMediatorConnection) {
    synchronized(this) {
      if (activeConnectionLazy.compareAndDrop(expectedConnection)) {
        parkedConnections.add(expectedConnection)
      }
    }
  }

  private fun removeParkedConnection(connection: ProcessMediatorConnection) {
    synchronized(this) {
      if (!activeConnectionLazy.compareAndDrop(connection)) {
        // note: it may not be there if ProcessMediatorClient.close() is called from dispose()
        parkedConnections.remove(connection)
      }
    }
  }

  fun adjustQuota(quotaOptions: QuotaOptions) = synchronized(this) {
    val connection = getActiveConnectionOrNull() ?: return
    connection.client.adjustQuotaBlocking(quotaOptions)
  }

  private fun launchDaemonAndConnect(): ProcessMediatorConnection = synchronized(this) {
    check(!isDisposed) { "Already disposed" }
    return connectionProvider()
  }

  override fun dispose() {
    val connections = synchronized(this) {
      if (isDisposed) return
      isDisposed = true

      activeConnectionLazy.drop()?.let { parkedConnections.add(it) }
      parkedConnections.toTypedArray().also {
        parkedConnections.clear()
      }
    }
    MultiCloseable.closeAll(*connections)
  }
}
