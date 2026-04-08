// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.rpc


import com.intellij.build.process.BuildProcessHandler
import com.intellij.execution.KillableProcess
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.Key
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import fleet.rpc.core.toRpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.future.asDeferred
import org.jetbrains.annotations.ApiStatus

/**
 * Returns [ProcessHandlerDto] which may be passed through RPC.
 *
 * @param coroutineScope provides the lifetime for given [ProcessHandlerDto] and its [ProcessHandlerDto.processHandlerId]
 */
@ApiStatus.Internal
fun createProcessHandlerDto(coroutineScope: CoroutineScope, processHandler: ProcessHandler): ProcessHandlerDto {
  val processHandlerId = processHandler.storeGlobally(coroutineScope)
  val flow = channelFlow {
    val listener = object : ProcessListener {
      override fun startNotified(event: ProcessEvent) {
        trySend(ProcessHandlerEvent.StartNotified(event.toRpc()))
      }

      override fun processTerminated(event: ProcessEvent) {
        trySend(ProcessHandlerEvent.ProcessTerminated(event.toRpc()))
        processHandler.removeProcessListener(this)
      }

      override fun processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean) {
        trySend(ProcessHandlerEvent.ProcessWillTerminate(event.toRpc(), willBeDestroyed))
      }

      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        trySend(ProcessHandlerEvent.OnTextAvailable(event.toRpc(), outputType.toString()))
      }

      override fun processNotStarted() {
        trySend(ProcessHandlerEvent.ProcessNotStarted)
      }
    }
    processHandler.addProcessListener(listener)

    // send initial state
    when {
      processHandler.isStartNotified -> {
        trySend(ProcessHandlerEvent.StartNotified(ProcessHandlerEventData(null, 0)))
      }
      processHandler.isProcessTerminating -> {
        trySend(ProcessHandlerEvent.StartNotified(ProcessHandlerEventData(null, 0)))
      }
      processHandler.isProcessTerminated -> {
        trySend(ProcessHandlerEvent.StartNotified(ProcessHandlerEventData(null, processHandler.exitCode ?: 0)))
      }
    }

    try {
      awaitClose()
    }
    finally {
      processHandler.removeProcessListener(listener)
    }
  }.buffer(Channel.UNLIMITED)

  val killableProcessInfo = if (processHandler is KillableProcess) {
    KillableProcessInfo(canKillProcess = processHandler.canKillProcess())
  }
  else {
    null
  }

  return ProcessHandlerDto(
    processHandlerId,
    (processHandler as? BuildProcessHandler)?.executionName,
    processHandler.detachIsDefault(),
    flow.toRpc(coroutineScope.coroutineContext),
    processHandler.nativePid?.asDeferred(),
    killableProcessInfo,
    processHandler,
  )
}

private fun ProcessEvent.toRpc(): ProcessHandlerEventData {
  return ProcessHandlerEventData(text, exitCode)
}


@ApiStatus.Internal
fun ProcessHandlerId.findValue(): ProcessHandler? {
  return findValueById(this, type = ProcessHandlerValueIdType)
}

@ApiStatus.Internal
fun ProcessHandler.storeGlobally(coroutineScope: CoroutineScope): ProcessHandlerId {
  return storeValueGlobally(coroutineScope, this, type = ProcessHandlerValueIdType)
}


@ApiStatus.Internal
private object ProcessHandlerValueIdType : BackendValueIdType<ProcessHandlerId, ProcessHandler>(::ProcessHandlerId)