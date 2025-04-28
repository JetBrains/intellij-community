// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.backend

import com.intellij.execution.KillableProcess
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.rpc.ProcessHandlerApi
import com.intellij.execution.rpc.ProcessHandlerId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import kotlinx.coroutines.*

internal class BackendProcessHandlerApi : ProcessHandlerApi {
  override suspend fun startNotify(handlerId: ProcessHandlerId) {
    val processHandler = handlerId.findValue() ?: return
    processHandler.startNotify()
  }

  override suspend fun waitFor(project: ProjectId, handlerId: ProcessHandlerId, timeoutInMilliseconds: Long?): Deferred<Boolean> {
    val processHandler = handlerId.findValue() ?: return CompletableDeferred(true)
    return project.findProject().service<BackendProcessHandlerApiCoroutineScope>().cs.async(Dispatchers.IO) {
      if (timeoutInMilliseconds != null) {
        processHandler.waitFor(timeoutInMilliseconds)
      }
      else {
        processHandler.waitFor()
      }
    }
  }

  override suspend fun destroyProcess(handlerId: ProcessHandlerId): Deferred<Int?> {
    val processHandler = handlerId.findValue() ?: return CompletableDeferred(value = null)
    return stopProcess(processHandler) {
      it.destroyProcess()
    }
  }

  override suspend fun detachProcess(handlerId: ProcessHandlerId): Deferred<Int?> {
    val processHandler = handlerId.findValue() ?: return CompletableDeferred(value = null)
    return stopProcess(processHandler) {
      it.detachProcess()
    }
  }

  override suspend fun killProcess(handlerId: ProcessHandlerId) {
    val processHandler = handlerId.findValue() ?: return
    if (processHandler is KillableProcess) {
      processHandler.killProcess()
    }
  }

  private fun stopProcess(processHandler: ProcessHandler, stopCallback: (ProcessHandler) -> Unit): Deferred<Int?> {
    val result = CompletableDeferred<Int?>()
    val listener = object : ProcessListener {
      override fun processTerminated(event: ProcessEvent) {
        processHandler.removeProcessListener(this)
        result.complete(event.exitCode)
      }
    }
    processHandler.addProcessListener(listener)
    if (processHandler.isProcessTerminated) {
      processHandler.removeProcessListener(listener)
      return CompletableDeferred(processHandler.exitCode)
    }
    stopCallback(processHandler)
    return result
  }
}


@Service(Service.Level.PROJECT)
private class BackendProcessHandlerApiCoroutineScope(val cs: CoroutineScope)
