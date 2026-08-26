// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.ijent.IjentCallerContext
import com.intellij.platform.ijent.ReconnectUiDialog
import com.intellij.platform.ijent.ReconnectUiHandle
import com.intellij.platform.ijent.unavailableDialogTimeout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Component
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

class IjentTimeoutException(message: String) : IOException(message)

sealed class IjentUnavailableHandlerResult {
  abstract fun throwException(): Nothing
  class ProjectCloseDecision(val eelDescriptor: EelDescriptor) : IjentUnavailableHandlerResult() {
    override fun throwException(): Nothing {
      throw IjentTimeoutException("User decided to close the project without waiting for not responding ijent $eelDescriptor.")
    }
  }
  class UnrelatedIjent(val eelDescriptor: EelDescriptor) : IjentUnavailableHandlerResult() {
    override fun throwException(): Nothing {
      throw IjentTimeoutException("Target $eelDescriptor is not related to any of open projects. Not waiting for it.")
    }
  }
}

interface IjentUnavailableHandler {
  suspend fun showModalDialog(eelDescriptor: EelDescriptor, uiHandle: ReconnectUiHandleImpl): IjentUnavailableHandlerResult
  companion object {
    val EP_NAME: ExtensionPointName<IjentUnavailableHandler> = ExtensionPointName("com.intellij.project.root.unavailable")
  }
}

class ReconnectUiHandleImpl : ReconnectUiHandle {
  private val requested = CompletableDeferred<Unit>()
  // should be interpreted as a plain flow of state, Pending -> Showing -> Pending -> Showing
  private val dialogStateFlowInternal = MutableStateFlow<Deferred<ReconnectUiDialogImpl>?>(null)
  val dialogStateFlow: Flow<ReconnectUiDialogImpl> get() = dialogStateFlowInternal.filterNotNull().map { it.await() }
  override suspend fun requestDialogImmediately(): ReconnectUiDialogImpl? {
    requested.complete(Unit)
    return withTimeoutOrNull(1.seconds) {
      dialogStateFlow.first()
    } ?: run {
      LOG.warn("No ijent-unavailable dialog shown despite requested. Probably a deadlock.")
      null
    }
  }
  fun requestDialog() {
    requested.complete(Unit)
  }
  suspend fun awaitRequested(): Unit = requested.await()
  fun setDialogSession(dialogSession: Deferred<ReconnectUiDialogImpl>) {
    dialogStateFlowInternal.value = dialogSession
  }
  companion object {
    private val LOG = Logger.getInstance(ReconnectUiHandleImpl::class.java)
  }
}

class ReconnectUiDialogImpl(val modalityState: ModalityState, override val component: Component) : ReconnectUiDialog {
  // modality technically doesn't need to be stored here, but it's a good indicator that a dialog is actually shown
  override val edtAndModality: CoroutineContext
    get() = Dispatchers.EDT + modalityState.asContextElement()
}

internal suspend fun <T> showModalDialogOnTimeout(eelDescriptor: EelDescriptor, callerContext: IjentCallerContext, body: suspend () -> T): T {
  // TODO behavior should depend on caller context:
  //  for EDT - basic events could be dispatched even before showing dialog.
  // TODO Now showing the dialog works only when EDT is free. In fact it's not free e.g. for DiskQueryRelay.

  val timeout = callerContext.unavailableDialogTimeout()
  val uiHandle = callerContext.reconnectUi as ReconnectUiHandleImpl
  return coroutineScope {
    val dialogJob = launch {
      // skip waiting for timeout if dialog is requested explicitly
      withTimeoutOrNull(timeout) {
        uiHandle.awaitRequested()
      } ?: uiHandle.requestDialog()
      val ijentUnavailableHandler = IjentUnavailableHandler.EP_NAME.extensionList.singleOrNull()
      try {
        ijentUnavailableHandler?.showModalDialog(eelDescriptor, uiHandle)?.throwException()
      }
      catch (e: Throwable) {
        uiHandle.setDialogSession(CompletableDeferred<ReconnectUiDialogImpl>().apply { completeExceptionally(e) })
        throw e
      }
      uiHandle.setDialogSession(CompletableDeferred<ReconnectUiDialogImpl>().apply { completeExceptionally(RuntimeException("No IjentUnavailableHandler found")) })
    }
    try {
      body()
    }
    finally {
      dialogJob.cancel()
    }
  }
}
