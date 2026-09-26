// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class IjentCallerContext(
  val isRead: Boolean,
  val isWrite: Boolean,
  val isDispatchThread: Boolean,
  val reconnectUi: ReconnectUiHandle,
) {
  companion object {
    suspend fun getSaved(): IjentCallerContext? {
      return getSavedElement()?.callerContext
    }

    suspend fun getSavedElement(): IjentCallerContextElement? {
      return currentCoroutineContext()[IjentCallerContextElement.Key]
    }
  }
}

fun IjentCallerContext.allowCancellableNio(): Boolean {
  return when {
    isRead && !isWrite -> IjentRegistry.getInstance().isEnabled("ijent.nio.cancellable.read", true)
    else -> false
  }
}

class IjentCallerContextElement(val callerContext: IjentCallerContext) : AbstractCoroutineContextElement(Key) {
  object Key : CoroutineContext.Key<IjentCallerContextElement>
}

/**
 * Throws if the current coroutine runs inside the synchronous IJent nio bridge
 * (see `fsBlocking` in `intellij.platform.ijent.community.impl`).
 *
 * Deploying IJent may require a round trip to EDT for user interaction, e.g., an SSH authentication dialog.
 * The bridge blocks the calling thread without pumping events, and the caller may be the EDT itself
 * or may hold the read lock, so a deployment that reaches EDT from inside it deadlocks the whole
 * application (IJPL-245001). Call this function at the start of a deployment that may require
 * user interaction to turn that deadlock into an exception.
 *
 * The caller that triggered the failed file system operation should initialize the environment in advance
 * (`com.intellij.platform.eel.provider.EelInitialization.runEelInitialization`) instead of relying on
 * an implicit interactive deployment.
 *
 * The check relies on [IjentCallerContextElement], which is installed by `fsBlockingWithoutParallelismCompensation`.
 * A deployment launched in a detached scope does not inherit the element, so code that awaits such
 * a deployment must call this function on the awaiting side; see `ijentFailSafeFileSystemApi`
 * in `intellij.platform.ijent.community.impl`.
 */
@ApiStatus.Internal
suspend fun throwIfInsideIjentFsBlocking() {
  if (currentCoroutineContext()[IjentCallerContextElement.Key] != null) {
    throw IllegalStateException(
      "IJent deployment is requested from inside a blocking IJent file system operation. " +
      "If the deployment needs user interaction (e.g., an SSH authentication dialog), it deadlocks: " +
      "the file system call blocks its thread, which may be EDT itself or may hold the read lock. " +
      "Initialize the environment in advance with EelInitialization instead (IJPL-245001)."
    )
  }
}

// TODO It is a copy-paste from Fleet, and it's better be generalized and put into some generic place.
fun CoroutineScope.coroutineNameAppended(name: String, separator: String = " > "): CoroutineContext =
  coroutineContext.coroutineNameAppended(name, separator)

fun CoroutineContext.coroutineNameAppended(name: String, separator: String = " > "): CoroutineContext {
  val parentName = this[CoroutineName]?.name
  return CoroutineName(if (parentName == null) name else parentName + separator + name)
}

/**
 * Provides scoped access to the modal UI shown while IJent is unavailable.
 *
 * The dialog solves two goals.
 * 1. It indicates the problem to the user when otherwise an application freeze would occur.
 * 2. It pumps EDT events so the reconnect or redeploy process in most situations can show some UI to ask for credentials.
 *
 * If not explicitly requested, the dialog can be shown after some timeout. But if requested, it will be shown as soon as possible.
 *
 * Several synchronous file-system calls may discover the same unavailable IJent concurrently. Only one unavailable
 * dialog can be shown at a time, so a caller does not necessarily receive a dialog created for its own request.
 * Instead, it joins the first compatible dialog that is actually shown.
 */
interface ReconnectUiHandle {
  
  /**
   * Requests an IJent-unavailable dialog and runs [f] while the selected dialog session is leased by this caller.
   *
   * Used when some UI has to be shown for reconnecting/redeploying IJent.
   *
   * The selected dialog is the first suitable dialog that is actually shown. It may have been initiated by another
   * concurrent file-system operation. This sharing is intentional: only one unavailable dialog can be active at a
   * time, and the caller which owns EDT wins because EDT is needed to show the dialog.
   *
   * The dialog must remain open while [f] executes. The callback may suspend and may use [ReconnectUiDialog.edtAndModality]
   * across those suspension points because its lease prevents the corresponding dialog session from being closed
   * merely because its original owner has completed.
   *
   * @throws IllegalStateException if no dialog can be shown within the implementation-defined timeout.
   */
  suspend fun <T> withRequestedDialog(f: suspend (dialog: ReconnectUiDialog) -> T): T
}

interface ReconnectUiDialog {
  val edtAndModality: CoroutineContext
  val component: Component
}