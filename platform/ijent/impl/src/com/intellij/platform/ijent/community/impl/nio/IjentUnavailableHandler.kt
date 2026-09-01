// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.ijent.IjentCallerContext
import com.intellij.platform.ijent.IjentRegistry
import com.intellij.platform.ijent.ReconnectUiDialog
import com.intellij.platform.ijent.ReconnectUiHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Component
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
      throw IjentTimeoutException("User decided to close the target $eelDescriptor which is not related to any of open projects.")
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
  // interpreted as a plain flow of state, Pending -> Showing -> Pending -> Showing
  private val dialogStateFlow = MutableStateFlow<Deferred<ReconnectUiDialogImpl>?>(null)
  @OptIn(ExperimentalCoroutinesApi::class)
  val currentDialog: ReconnectUiDialogImpl?
    get() {
      val session = dialogStateFlow.value ?: return null
      if (!session.isCompleted || session.isCancelled) return null
      if (session.getCompletionExceptionOrNull() != null) return null
      return session.getCompleted()
    }
  override suspend fun <T> withRequestedDialog(f: suspend (dialog: ReconnectUiDialog) -> T): T {
    requested.complete(Unit)
    val dialogShown = withTimeoutOrNull(1.seconds) {
      dialogStateFlow.filterNotNull().map { it.await() }.first()
    } ?: run {
      throw IllegalStateException("Could not show ijent-unavailable dialog. Probably a deadlock.")
    }
    dialogShown.leaseCount.getAndUpdate { if (it >= 0) it + 1 else it }.let {
      if (it < 0) throw IllegalStateException("Could not show ijent-unavailable dialog. Dialog disappeared.")
    }
    try {
      return f(dialogShown)
    }
    finally {
      dialogShown.leaseCount.update { it - 1 }
    }
  }
  fun requestDialog() {
    requested.complete(Unit)
  }
  suspend fun awaitRequested(): Unit = requested.await()
  fun setDialogSession(dialogSession: Deferred<ReconnectUiDialogImpl>) {
    dialogStateFlow.value = dialogSession
  }
}

class ReconnectUiDialogImpl(val modalityState: ModalityState, override val component: Component) : ReconnectUiDialog {
  // positive if somebody is using the dialog, negative if the dialog is disposed
  val leaseCount: MutableStateFlow<Int> = MutableStateFlow(0)
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
    val dialogJob = if (IjentRegistry.getInstance().isEnabled("ijent.unavailable.dialog.enabled", true)) {
      launch {
        // skip waiting for timeout if dialog is requested explicitly
        withTimeoutOrNull(timeout) {
          uiHandle.awaitRequested()
        } ?: uiHandle.requestDialog()
        val ijentUnavailableHandler = IjentUnavailableHandler.EP_NAME.extensionList.singleOrNull()
        if (ijentUnavailableHandler == null) {
          uiHandle.setDialogSession(CompletableDeferred<ReconnectUiDialogImpl>().apply { completeExceptionally(RuntimeException("No IjentUnavailableHandler found")) })
        }
        else {
          try {
            ijentUnavailableHandler.showModalDialog(eelDescriptor, uiHandle).throwException()
          }
          catch (e: Throwable) {
            uiHandle.setDialogSession(CompletableDeferred<ReconnectUiDialogImpl>().apply { completeExceptionally(e) })
            if (e !is CancellationException) {
              LOG.warn(e)
            }
            throw e
          }
        }
      }
    }
    else null
    try {
      body()
    }
    finally {
      withContext(NonCancellable) {
        uiHandle.currentDialog?.apply {
          do {
            val released = leaseCount.first { it <= 0 }
            val updated = leaseCount.compareAndSet(released, -1)
          } while (!updated)
        }
      }
      dialogJob?.cancel()
    }
  }
}

private fun IjentCallerContext.unavailableDialogTimeout(): Duration {
  // The numbers here make no real sense. It's just some assumptions how long outage a user can tolerate.
  // It depends on which resources are locked by the requests that happen to freeze.
  return when {
    isDispatchThread || isWrite -> 500.milliseconds
    isRead -> 1.seconds
    else -> 10.seconds
  }
}

private val LOG = Logger.getInstance(IjentUnavailableHandler::class.java)