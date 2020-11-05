// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.mediator.MediatedProcess
import com.intellij.execution.process.mediator.ProcessMediatorClient
import com.intellij.execution.process.mediator.daemon.QuotaExceededException
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.BaseOutputReader

@Service
class ElevationService : Disposable {
  companion object {
    @JvmStatic
    fun getInstance() = service<ElevationService>()
  }

  private val clientManager = ProcessMediatorClientManager().also {
    Disposer.register(this, it)
  }

  fun createProcess(commandLine: GeneralCommandLine): OSProcessHandler {
    val processBuilder = commandLine.toProcessBuilder()
    val process = tryRelaunchingDaemonUntilHaveQuotaPermit { client ->
      MediatedProcess.create(client, processBuilder).apply {
        ElevationLogger.LOG.info("Created process PID ${pid()}")
      }
    }
    return object : OSProcessHandler(process, commandLine.commandLineString, commandLine.charset) {
      override fun readerOptions(): BaseOutputReader.Options {
        return BaseOutputReader.Options.BLOCKING  // our ChannelInputStream unblocks read() on close()
      }
    }
  }

  private fun <R> tryRelaunchingDaemonUntilHaveQuotaPermit(block: (ProcessMediatorClient) -> R): R {
    while (true) {
      val client = clientManager.launchDaemonAndConnectClientIfNeeded()
      try {
        return block(client)
      }
      catch (e: QuotaExceededException) {
        clientManager.parkClient(client)
      }
    }
  }

  override fun dispose() = Unit
}
