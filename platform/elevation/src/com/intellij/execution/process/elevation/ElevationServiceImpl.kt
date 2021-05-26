// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.application.subscribe
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ElevationService
import com.intellij.execution.process.elevation.settings.ElevationSettings
import com.intellij.execution.process.mediator.client.MediatedProcess
import com.intellij.execution.process.mediator.client.MediatedProcessHandler
import com.intellij.execution.process.mediator.client.ProcessMediatorClient
import com.intellij.execution.process.mediator.daemon.QuotaExceededException
import com.intellij.execution.process.mediator.daemon.QuotaOptions
import com.intellij.execution.process.mediator.launcher.ProcessMediatorConnection
import com.intellij.execution.process.mediator.launcher.ProcessMediatorConnectionManager
import com.intellij.execution.process.mediator.launcher.startInProcessServer
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.RejectedExecutionException
import kotlin.coroutines.EmptyCoroutineContext

class ElevationServiceImpl : ElevationService, Disposable {
  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)

  private val connectionManager = ProcessMediatorConnectionManager {
    val clientBuilder = ProcessMediatorClient.Builder(coroutineScope, ElevationSettings.getInstance().quotaOptions)

    val debug = false
    if (debug) ProcessMediatorConnection.startInProcessServer(coroutineScope, clientBuilder = clientBuilder)
    else ElevationDaemonProcessLauncher(clientBuilder)
      .launchWithProgress(ElevationBundle.message("progress.title.starting.elevation.daemon"))
  }.apply {
    ElevationSettings.Listener.TOPIC.subscribe(this, object : ElevationSettings.Listener {
      override fun onDaemonQuotaOptionsChanged(oldValue: QuotaOptions, newValue: QuotaOptions) {
        adjustQuota(newValue)
      }
    })
  }.also {
    Disposer.register(this, it)
  }

  override fun authorizeService() {
    connectionManager.launchDaemonAndConnectIfNeeded()
  }

  override fun createProcessHandler(commandLine: GeneralCommandLine): MediatedProcessHandler {
    val processBuilder = commandLine.toProcessBuilder()
    val process = createProcess(processBuilder)
    return MediatedProcessHandler(process, commandLine)
  }

  override fun createProcess(processBuilder: ProcessBuilder): MediatedProcess {
    if (!ElevationSettings.getInstance().askEnableKeepAuthIfNeeded()) {
      throw ProcessCanceledException()
    }
    return tryRelaunchingDaemonUntilHaveQuotaPermit { client ->
      MediatedProcess.create(client, processBuilder).apply {
        ElevationLogger.LOG.info("Created process PID ${pid()}")
      }
    }
  }

  private fun <R> tryRelaunchingDaemonUntilHaveQuotaPermit(block: (ProcessMediatorClient) -> R): R {
    val maxAttempts = MAX_RELAUNCHING_DAEMON_UNTIL_HAVE_QUOTA_PERMIT_ATTEMPTS
    for (attempt in 1..maxAttempts) {
      val connection = connectionManager.launchDaemonAndConnectIfNeeded()
      try {
        return block(connection.client)
      }
      catch (e: Exception) {
        if (e !is RejectedExecutionException &&
            e !is QuotaExceededException) throw e
        if (attempt > 1) ElevationLogger.LOG.warn("Repeated launch error after $attempt attempts; " +
                                                  "quota options: ${ElevationSettings.getInstance().quotaOptions}", e)
        connectionManager.parkConnection(connection)
      }
    }
    throw ExecutionException(ElevationBundle.message("dialog.message.unable.to.configure.elevation.daemon.after.attempts",
                                                     maxAttempts))
  }

  override fun dispose() = Unit

  companion object {
    private const val MAX_RELAUNCHING_DAEMON_UNTIL_HAVE_QUOTA_PERMIT_ATTEMPTS = 3
  }
}
