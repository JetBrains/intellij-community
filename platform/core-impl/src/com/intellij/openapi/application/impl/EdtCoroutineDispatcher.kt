// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.EdtDispatcherKind.LockBehavior
import com.intellij.openapi.progress.isRunBlockingUnderReadAction
import com.intellij.util.ui.EDT
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

/**
 * Default UI dispatcher, it dispatches within the [context modality state][com.intellij.openapi.application.asContextElement].
 *
 * Cancelled coroutines are dispatched [without modality state][ModalityState.any] to be able to finish fast. Such coroutines are not
 * expected to access the model (PSI/VFS/etc).
 *
 * This dispatcher is installed as [main][kotlinx.coroutines.Dispatchers.Main] with disabled access to locks and default modality as
 * [ModalityState.any]. See IJPL-166436
 */
internal sealed class EdtCoroutineDispatcher(
  protected val type: EdtDispatcherKind,
) : MainCoroutineDispatcher() {

  override val immediate: MainCoroutineDispatcher
    get() = when (type) {
      EdtDispatcherKind.LEGACY_EDT -> ImmediateLockingDispatcher
      EdtDispatcherKind.MODERN_UI -> ImmediateNonLockingDispatcher
      EdtDispatcherKind.MAIN -> ImmediateMainDispatcher
    }

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    check(!context.isRunBlockingUnderReadAction()) {
      "Switching to `$this` from `runBlockingCancellable` inside in a read-action leads to possible deadlock."
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

  protected fun CoroutineContext.effectiveContextModality(): ModalityState =
    contextModality() ?: type.fallbackModality.toModalityState()

  override fun toString(): String {
    return type.presentableName()
  }

  private fun wrapWithLocking(runnable: Runnable): Runnable {
    if (!type.allowLocks()) {
      return Runnable {
        try {
          ApplicationManagerEx.getApplicationEx().prohibitTakingLocksInsideAndRun(runnable, type.lockBehavior == LockBehavior.LOCKS_DISALLOWED_FAIL_SOFT)
        }
        catch (e: ThreadingSupport.LockAccessDisallowed) {
          throw IllegalStateException("You are attempting to use the RW lock inside `$this`.\n" +
                                      "This dispatcher is intended for pure UI operations, which do not interact with the IntelliJ Platform model (PSI, VFS, etc.).\n" +
                                      "The following solutions are available:\n" +
                                      "1. Consider moving the model access outside `$this`. This would help to ensure that the UI is responsive.\n" +
                                      "2. Consider using legacy `Dispatchers.EDT` that allows using the RW lock.\n", e)
        }
      }
    }
    if (isCoroutineWILEnabled) {
      return Runnable {
        WriteIntentReadAction.run {
          runnable.run()
        }
      }
    }
    return runnable
  }

  object Locking : EdtCoroutineDispatcher(EdtDispatcherKind.LEGACY_EDT)
  object NonLocking : EdtCoroutineDispatcher(EdtDispatcherKind.MODERN_UI)
  object Main : EdtCoroutineDispatcher(EdtDispatcherKind.MAIN)
}

private class ImmediateEdtCoroutineDispatcher(type: EdtDispatcherKind) : EdtCoroutineDispatcher(type) {
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
    if (type.allowLocks()) {
      // this dispatcher requires RW lock => if EDT does not the hold lock, then we need to reschedule to avoid blocking
      return !ApplicationManager.getApplication().isWriteIntentLockAcquired
    }
    return false
  }

  override fun toString(): String {
    return super.toString() + ".immediate"
  }
}

internal enum class EdtDispatcherKind(
  /**
   * Historically, all runnables executed on EDT were wrapped into a Write-Intent lock. We want to move away from this contract, so here
   * we operate with two versions of EDT-thread dispatcher. If [lockBehavior] == [LockBehavior.LOCKS_ALLOWED], then all runnables are
   * automatically wrapped into write-intent lock If [lockBehavior] != [LockBehavior.LOCKS_ALLOWED], then runnables are executed without the
   * lock, and requesting lock is forbidden for them.
   */
  val lockBehavior: LockBehavior,
  /**
   * Sometimes coroutines get dispatched without an explicit modality in their contexts. In this case, we differentiate two cases:
   * 1. Platform-relevant dispatchers that may access the IJ Platform model (`Dispatchers.UI` and `Dispatchers.EDT`) These dispatchers
   *    should be aware of modality, and hence they use `NON_MODAL` as the default modality. It is a safe choice that helps to ensure model
   *    consistency and detect issues with absent modality faster.
   * 2. Commonly visible dispatchers (Dispatchers.Main) This dispatcher can be invoked from any third party library, and there are no
   *    guarantees that the surrounding code uses modality contracts correctly. Even worse, there may be no way to pass modality to this
   *    code. Here we use `any` modality, as it ensures progress guarantees.
   */
  val fallbackModality: DefaultModality,
) {


  LEGACY_EDT(LockBehavior.LOCKS_ALLOWED, DefaultModality.NON_MODAL),

  MODERN_UI(LockBehavior.LOCKS_DISALLOWED_FAIL_HARD, DefaultModality.NON_MODAL),

  MAIN(LockBehavior.LOCKS_DISALLOWED_FAIL_SOFT, DefaultModality.ANY);

  enum class DefaultModality {
    ANY,
    NON_MODAL;

    fun toModalityState(): ModalityState = when (this) {
      ANY -> ModalityState.any()
      NON_MODAL -> ModalityState.nonModal()
    }
  }

  enum class LockBehavior {
    LOCKS_ALLOWED,
    LOCKS_DISALLOWED_FAIL_SOFT,
    LOCKS_DISALLOWED_FAIL_HARD,
  }

  fun allowLocks(): Boolean = lockBehavior == LockBehavior.LOCKS_ALLOWED

  fun presentableName(): String = when (this) {
    LEGACY_EDT -> "Dispatchers.EDT"
    MODERN_UI -> "Dispatchers.UI"
    MAIN -> "Dispatchers.UI.Main"
  }
}

private val ImmediateLockingDispatcher = ImmediateEdtCoroutineDispatcher(EdtDispatcherKind.LEGACY_EDT)
private val ImmediateNonLockingDispatcher = ImmediateEdtCoroutineDispatcher(EdtDispatcherKind.MODERN_UI)
private val ImmediateMainDispatcher = ImmediateEdtCoroutineDispatcher(EdtDispatcherKind.MAIN)