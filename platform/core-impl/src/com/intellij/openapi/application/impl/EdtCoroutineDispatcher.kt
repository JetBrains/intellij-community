// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ThreadingSupport
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.isCoroutineWILEnabled
import com.intellij.openapi.progress.isRunBlockingUnderReadAction
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.EDT
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.job
import java.lang.IllegalStateException
import kotlin.coroutines.CoroutineContext

/**
 * Default UI dispatcher, it dispatches within the [context modality state][com.intellij.openapi.application.asContextElement].
 *
 * Cancelled coroutines are dispatched [without modality state][ModalityState.any] to be able to finish fast.
 * Such coroutines are not expected to access the model (PSI/VFS/etc).
 *
 * This dispatcher is installed as [main][kotlinx.coroutines.Dispatchers.Main].
 */
internal sealed class EdtCoroutineDispatcher(
  /**
   * Historically, all runnables executed on EDT were wrapped into a Write-Intent lock.
   * We want to move away from this contract, so here we operate with two versions of EDT-thread dispatcher.
   * If [allowReadWriteLock] == `true`, then all runnables are automatically wrapped into write-intent lock
   * If [allowReadWriteLock] == `false`, then runnables are executed without the lock, and requesting lock is forbidden for them.
   */
  val allowReadWriteLock: Boolean,
) : MainCoroutineDispatcher() {

  override val immediate: MainCoroutineDispatcher = if (allowReadWriteLock) LockingImmediateDispatcher else NonLockingImmediateDispatcher

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    check(!context.isRunBlockingUnderReadAction()) {
      "Switching to `${allowReadWriteLock.dispatcherName}` from `runBlockingCancellable` inside in a read-action leads to possible deadlock."
    }
    val lockingAwareBlock = wrapWithLocking(block)
    val state = context.effectiveContextModality()
    val runnable = if (state === ModalityState.any()) {
      ContextAwareRunnable(lockingAwareBlock::run)
    }
    else {
      DispatchedRunnable(context.job, lockingAwareBlock)
    }
    ApplicationManagerEx.getApplicationEx().dispatchCoroutineOnEDT(runnable, state)
  }

  override fun toString(): String {
    return allowReadWriteLock.dispatcherName
  }

  private fun wrapWithLocking(runnable: Runnable): Runnable {
    if (!allowReadWriteLock) {
      return Runnable {
        try {
          ApplicationManagerEx.getApplicationEx().prohibitTakingLocksInsideAndRun(runnable)
        }
        catch (e: ThreadingSupport.LockAccessDisallowed) {
          throw IllegalStateException("You are attempting to use the RW lock inside `Dispatchers.UI`.\n" +
                                      "This dispatcher is intended for pure UI operations, which do not interact with the IntelliJ Platform model (PSI, VFS, etc.).\n" +
                                      "The following solutions are available:\n" +
                                      "1. Consider moving the model access outside `Dispatchers.UI`. This would help to ensure that the UI is responsive.\n" +
                                      "2. Consider using legacy `Dispatchers.EDT` that allows using the RW lock.\n", e)
        }
      }
    }
    if (isCoroutineWILEnabled) {
      return Runnable {
        WriteIntentReadAction.run {
          ThreadingAssertions.setImplicitLockOnEDT(true)
          try {
            runnable.run()
          }
          finally {
            ThreadingAssertions.setImplicitLockOnEDT(false)
          }
        }
      }
    }
    return runnable
  }

  object Locking : EdtCoroutineDispatcher(true)
  object NonLocking : EdtCoroutineDispatcher(false)
}

private class ImmediateEdtCoroutineDispatcher(allowReadWriteLock: Boolean) : EdtCoroutineDispatcher(allowReadWriteLock) {
  override fun isDispatchNeeded(context: CoroutineContext): Boolean {
    // The current coroutine is executed with the correct modality state
    // (the execution would be postponed otherwise).
    // But the code that's about to be executed may belong to a different coroutine
    // (e.g., one coroutine emits a value into a flow and another collects it).
    // If the context modality is lower than the current modality,
    // we need to dispatch and postpone its execution.
    if (!EDT.isCurrentThreadEdt()) {
      return true
    }
    val contextModality = context.effectiveContextModality()
    // If the context modality is explicitly any(), then no dispatch is performed,
    // as dominates(any()) always returns false, no special any() handling required here.
    if (!ModalityState.current().accepts(contextModality)) {
      return true
    }
    if (allowReadWriteLock) {
      // this dispatcher requires RW lock => if EDT does not the hold lock, then we need to reschedule to avoid blocking
      return !ApplicationManager.getApplication().isWriteIntentLockAcquired
    }
    else {
      // this dispatcher forbids RW lock => we need to reschedule to release the lock
      return ApplicationManager.getApplication().isWriteIntentLockAcquired
    }
  }

  override fun toString(): String = "${this@ImmediateEdtCoroutineDispatcher.allowReadWriteLock.dispatcherName}.immediate"
}

private val LockingImmediateDispatcher = ImmediateEdtCoroutineDispatcher(true)
private val NonLockingImmediateDispatcher = ImmediateEdtCoroutineDispatcher(false)


private fun CoroutineContext.effectiveContextModality(): ModalityState =
  contextModality() ?: ModalityState.nonModal() // dispatch with NON_MODAL by default

private val Boolean.dispatcherName
  get() = when (this) {
    true -> "Dispatchers.EDT"
    false -> "Dispatchers.UI"
  }