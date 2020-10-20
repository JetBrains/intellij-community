// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.mediator.MediatedProcess
import com.intellij.execution.process.mediator.ProcessMediatorClient
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.io.BaseOutputReader
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext

@Service
class ElevationService : Disposable {
  companion object {
    @JvmStatic
    fun getInstance() = service<ElevationService>()
  }

  //private val elevatorClient: ProcessMediatorClient by elevatorClientLazy

  override fun dispose() {
    //elevatorClientLazy.drop()?.close()
  }

  fun createProcess(commandLine: GeneralCommandLine): OSProcessHandler {
    val coroutineScope = CoroutineScope(EmptyCoroutineContext)
    val daemon = ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
      ProcessMediatorDaemonLauncher.launchDaemon(sudo = true)
    }, ElevationBundle.message("progress.title.starting.elevation.daemon"), true, null)
    val channel = daemon.createChannel()
    val elevatorClient = ProcessMediatorClient(coroutineScope, channel)

    val process = MediatedProcess.create(elevatorClient, commandLine.toProcessBuilder()).apply {
      onExit().whenComplete { _, _ ->
        elevatorClient.close()
      }
      ElevationLogger.LOG.debug("Created process PID ${pid()}")
    }
    return object : OSProcessHandler(process, commandLine.commandLineString, commandLine.charset) {
      override fun readerOptions(): BaseOutputReader.Options {
        return BaseOutputReader.Options.BLOCKING  // our ChannelInputStream unblocks read() on close()
      }
    }
  }
}
