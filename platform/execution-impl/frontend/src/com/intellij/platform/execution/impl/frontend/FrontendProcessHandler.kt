// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.frontend

import com.intellij.execution.KillableProcess
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.rpc.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.OutputStream

@ApiStatus.Internal
fun createFrontendProcessHandler(
  project: Project,
  processHandlerDto: ProcessHandlerDto,
): FrontendSessionProcessHandler {
  val killableProcessInfo = processHandlerDto.killableProcessInfo
  return if (killableProcessInfo != null) {
    FrontendSessionKillableProcessHandler(project, processHandlerDto, killableProcessInfo)
  }
  else {
    FrontendSessionProcessHandler(project, processHandlerDto)
  }
}


// TODO: check for possible races because of cs.launch in all class methods
@ApiStatus.Internal
open class FrontendSessionProcessHandler(
  private val project: Project,
  protected val processHandlerDto: ProcessHandlerDto,
) : ProcessHandler() {
  // TODO: use better CoroutineScope
  protected val cs: CoroutineScope = project.service<FrontendSessionProcessHandlerCoroutineScope>().cs

  protected val handlerId: ProcessHandlerId = processHandlerDto.processHandlerId

  init {
    cs.launch(Dispatchers.EDT) {
      processHandlerDto.processHandlerEvents.toFlow().collect { event ->
        when (event) {
          is ProcessHandlerEvent.StartNotified -> {
            if (!isStartNotified) {
              startNotify()
            }
          }
          is ProcessHandlerEvent.ProcessTerminated -> {
            destroyProcess()
          }
          is ProcessHandlerEvent.ProcessWillTerminate -> {
            destroyProcess()
          }
          is ProcessHandlerEvent.OnTextAvailable -> {
            // TODO: DONT create Key every time
            notifyTextAvailable(event.eventData.text ?: "", Key.create<Any?>(event.key))
          }
          ProcessHandlerEvent.ProcessNotStarted -> {
            // TODO: handle this case
          }
        }
      }
    }
  }

  override fun waitFor(): Boolean {
    return runBlockingMaybeCancellable {
      ProcessHandlerApi.getInstance().waitFor(project.projectId(), handlerId, null).await()
    }
  }

  override fun waitFor(timeoutInMilliseconds: Long): Boolean {
    return runBlockingMaybeCancellable {
      ProcessHandlerApi.getInstance().waitFor(project.projectId(), handlerId, timeoutInMilliseconds).await()
    }
  }

  private fun triggerInitialEvents(listener: ProcessListener) {
    // Some process listeners may be added when process is already going
    if (isStartNotified) {
      listener.startNotified(ProcessEvent(this))
    }
    if (isProcessTerminated) {
      listener.processTerminated(ProcessEvent(this, exitCode ?: 0))
    }
  }

  override fun addProcessListener(listener: ProcessListener) {
    triggerInitialEvents(listener)
    super.addProcessListener(listener)
  }

  override fun addProcessListener(listener: ProcessListener, parentDisposable: Disposable) {
    triggerInitialEvents(listener)
    super.addProcessListener(listener, parentDisposable)
  }

  override fun destroyProcessImpl() {
    cs.launch {
      val exitCode = ProcessHandlerApi.getInstance().destroyProcess(handlerId).await()
      notifyProcessTerminated(exitCode ?: 0)
    }
  }

  override fun detachProcessImpl() {
    cs.launch {
      ProcessHandlerApi.getInstance().detachProcess(handlerId).await()
      notifyProcessDetached()
    }
  }

  override fun detachIsDefault(): Boolean {
    return processHandlerDto.detachIsDefault
  }

  override fun getProcessInput(): OutputStream? {
    LOG.error("getProcessInput shouldn't be used on the frontend")
    return null
  }
}

private class FrontendSessionKillableProcessHandler(
  project: Project,
  processHandlerDto: ProcessHandlerDto,
  private val killableProcessInfo: KillableProcessInfo,
) : FrontendSessionProcessHandler(project, processHandlerDto), KillableProcess {

  override fun canKillProcess(): Boolean {
    return killableProcessInfo.canKillProcess
  }

  override fun killProcess() {
    if (canKillProcess()) {
      cs.launch {
        ProcessHandlerApi.getInstance().killProcess(handlerId)
      }
    }
  }
}

private val LOG = fileLogger()

@Service(Service.Level.PROJECT)
private class FrontendSessionProcessHandlerCoroutineScope(val cs: CoroutineScope)