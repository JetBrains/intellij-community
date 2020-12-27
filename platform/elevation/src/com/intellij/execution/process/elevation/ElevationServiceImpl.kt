// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ElevationService
import com.intellij.execution.process.SelfKiller
import com.intellij.execution.process.elevation.settings.ElevationSettings
import com.intellij.execution.process.mediator.MediatedProcessHandler
import com.intellij.execution.process.mediator.ProcessMediatorClientManager
import com.intellij.execution.process.mediator.client.MediatedProcess
import com.intellij.execution.process.mediator.client.ProcessMediatorClient
import com.intellij.execution.process.mediator.daemon.QuotaExceededException
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import io.grpc.ManagedChannel
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext

class ElevationServiceImpl : ElevationService, Disposable {
  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)
  private val clientManager = ProcessMediatorClientManager(ElevationDaemonLauncher()::launchDaemon,
                                                           ::createProcessMediatorClient).also {
    Disposer.register(this, it)
  }

  private fun createProcessMediatorClient(channel: ManagedChannel): ProcessMediatorClient {
    return ProcessMediatorClient(coroutineScope, channel, ElevationSettings.getInstance().quotaOptions)
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
      object : MediatedProcess(client, processBuilder), SelfKiller {
        init {
          ElevationLogger.LOG.info("Created process PID ${pid()}")
        }
      }
    }
  }

  private fun <R> tryRelaunchingDaemonUntilHaveQuotaPermit(block: (ProcessMediatorClient) -> R): R {
    val maxAttempts = MAX_RELAUNCHING_DAEMON_UNTIL_HAVE_QUOTA_PERMIT_ATTEMPTS
    for (attempt in 1..maxAttempts) {
      val client = clientManager.launchDaemonAndConnectClientIfNeeded()
      try {
        return block(client)
      }
      catch (e: QuotaExceededException) {
        if (attempt > 1) ElevationLogger.LOG.warn("Repeated quota exceeded error after $attempt attempts; " +
                                                  "quota options: ${ElevationSettings.getInstance().quotaOptions}", e)
        clientManager.parkClient(client)
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
