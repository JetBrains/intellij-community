// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.ijent.IjentCallerContext
import com.intellij.platform.ijent.ReconnectUiDialog
import com.intellij.platform.ijent.ReconnectUiHandle
import com.intellij.platform.ijent.unavailableDialogTimeout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

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
  suspend fun showModalDialog(eelDescriptor: EelDescriptor, uiHandle: ReconnectUiDialogImpl): IjentUnavailableHandlerResult
  companion object {
    val EP_NAME: ExtensionPointName<IjentUnavailableHandler> = ExtensionPointName("com.intellij.project.root.unavailable")
  }
}

internal class ReconnectUiHandleImpl : ReconnectUiHandle {
  private val requested = CompletableDeferred<ReconnectUiDialogImpl>()
  override fun requestDialogImmediately(): ReconnectUiDialog {
    val result = ReconnectUiDialogImpl()
    if (requested.complete(result)) {
      return result
    }
    else {
      @OptIn(ExperimentalCoroutinesApi::class)
      return requested.getCompleted()
    }
  }
  suspend fun awaitRequested(): ReconnectUiDialogImpl = requested.await()
}

class ReconnectUiDialogImpl : ReconnectUiDialog

internal suspend fun <T> showModalDialogOnTimeout(eelDescriptor: EelDescriptor, callerContext: IjentCallerContext, body: suspend () -> T): T {
  // TODO behavior should depend on caller context:
  //  for EDT - basic events could be dispatched even before showing dialog.
  // TODO Now showing the dialog works only when EDT is free. In fact it's not free e.g. for DiskQueryRelay.

  val timeout = callerContext.unavailableDialogTimeout()
  val uiHandle = callerContext.reconnectUi as ReconnectUiHandleImpl
  return coroutineScope {
    val dialogJob = launch {
      // skip waiting for timeout if dialog is requested explicitly
      val dialog = withTimeoutOrNull(timeout) {
        uiHandle.awaitRequested()
      } ?: run {
        uiHandle.requestDialogImmediately()
        uiHandle.awaitRequested()
      }
      val ijentUnavailableHandler = IjentUnavailableHandler.EP_NAME.extensionList.singleOrNull()
      ijentUnavailableHandler?.showModalDialog(eelDescriptor, dialog)?.throwException()
    }
    try {
      body()
    }
    finally {
      dialogJob.cancel()
    }
  }
}
