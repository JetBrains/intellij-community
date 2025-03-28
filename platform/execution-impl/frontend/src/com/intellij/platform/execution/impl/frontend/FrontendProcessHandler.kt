// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.impl.frontend

import com.intellij.execution.KillableProcess
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.rpc.KillableProcessInfo
import com.intellij.execution.rpc.ProcessHandlerApi
import com.intellij.execution.rpc.ProcessHandlerDto
import com.intellij.execution.rpc.ProcessHandlerEvent
import com.intellij.execution.rpc.ProcessHandlerId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
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

  /**
   * The wall which prevents events to be sent to listeners before all the listeners are attached
   * This is like [startNotify] on the backend.
   *
   * So, [setReady] should be called to send all the buffered events to the listeners.
   *
   * Without that flag listeners may be after we send events to the listeners,
   * so events like [startNotify] will be missed by the newly attached listeners.
   */
  private val isReady = CompletableDeferred<Unit>()

  init {
    val events = Channel<() -> Unit>(capacity = Channel.UNLIMITED)
    cs.launch {
      processHandlerDto.processHandlerEvents.toFlow().collect { event ->
        when (event) {
          is ProcessHandlerEvent.StartNotified -> {
            if (!isStartNotified) {
              events.trySend {
                startNotify()
              }
            }
          }
          is ProcessHandlerEvent.ProcessTerminated -> {
            events.trySend {
              destroyProcess()
            }
          }
          is ProcessHandlerEvent.ProcessWillTerminate -> {
            events.trySend {
              destroyProcess()
            }
          }
          is ProcessHandlerEvent.OnTextAvailable -> {
            // TODO: DONT create Key every time
            events.trySend {
              notifyTextAvailable(event.eventData.text ?: "", Key.create<Any?>(event.key))
            }
          }
          ProcessHandlerEvent.ProcessNotStarted -> {
            // TODO: handle this case
          }
        }
      }
    }

    cs.launch {
      isReady.await()
      for (event in events) {
        event()
      }
    }
  }

  fun setReady() {
    isReady.complete(Unit)
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