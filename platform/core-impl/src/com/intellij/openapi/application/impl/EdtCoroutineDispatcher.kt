// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
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
      EdtDispatcherKind.EDT -> ImmediateEdtDispatcher
      EdtDispatcherKind.LAX_UI -> ImmediateLaxUiDispatcher
      EdtDispatcherKind.UI -> ImmediateUiDispatcher
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
    return when (type.lockBehavior) {
      EdtDispatcherKind.LockBehavior.LOCKS_DISALLOWED_FAIL_HARD -> {
        Runnable {
          ApplicationManagerEx.getApplicationEx().prohibitTakingLocksInsideAndRun(runnable, false, lockAccessViolationMessage)
        }
      }
      EdtDispatcherKind.LockBehavior.LOCKS_ALLOWED_NO_WRAPPING -> {
        runnable
      }
      EdtDispatcherKind.LockBehavior.LOCKS_ALLOWED_MANDATORY_WRAPPING -> {
        return if (isCoroutineWILEnabled) {
          Runnable {
            WriteIntentReadAction.run {
              runnable.run()
            }
          }
        }
        else {
          runnable
        }
      }
    }
  }

  object LockWrapping : EdtCoroutineDispatcher(EdtDispatcherKind.EDT)
  object NonLocking : EdtCoroutineDispatcher(EdtDispatcherKind.LAX_UI)
  object LockForbidden : EdtCoroutineDispatcher(EdtDispatcherKind.UI)
  object Main : EdtCoroutineDispatcher(EdtDispatcherKind.MAIN)

  val lockAccessViolationMessage = """The use of the RW lock is forbidden by `$this`. This dispatcher is intended for pure UI operations, which do not interact with the IntelliJ Platform model (PSI, VFS, etc.).
The following solutions are available:
1. Consider moving the model access outside `$this`. This would help to ensure that the UI is responsive.
2. Consider using legacy `Dispatchers.EDT` that permits usage of the RW lock. In this case, you can wrap the model-accessing code in `Dispatchers.EDT`
"""
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
    return when (type) {
      // `Dispatchers.Main.immediate` must look only at the thread where it is executing.
      // This is needed to support 3rd party libraries which use this dispatcher for getting the UI thread
      // Relaxed UI dispatcher is indifferent to locks, so it can run in-place.
      EdtDispatcherKind.MAIN, EdtDispatcherKind.LAX_UI -> false
      // `Dispatchers.EdtImmediate` must perform dispatch when invoked on a thread without locks, because it needs to get into correct context.
      EdtDispatcherKind.EDT -> !ApplicationManager.getApplication().isWriteIntentLockAcquired
      // `Dispatchers.UIImmediate` must perform dispatch when invoked on a thread with locks, because it needs to escape locking and forbid using them inside.
      EdtDispatcherKind.UI -> ApplicationManager.getApplication().isWriteIntentLockAcquired
    }
  }

  override fun toString(): String {
    return super.toString() + ".immediate"
  }
}

internal enum class EdtDispatcherKind(
  /**
   * Historically, all runnables executed on EDT were wrapped into a Write-Intent lock. We want to move away from this contract, so here
   * we operate with two versions of EDT-thread dispatcher. If [lockBehavior] == [LockBehavior.LOCKS_ALLOWED_MANDATORY_WRAPPING], then all runnables are
   * automatically wrapped into write-intent lock If [lockBehavior] != [LockBehavior.LOCKS_ALLOWED_MANDATORY_WRAPPING], then runnables are executed without the
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


  EDT(LockBehavior.LOCKS_ALLOWED_MANDATORY_WRAPPING, DefaultModality.NON_MODAL),

  LAX_UI(LockBehavior.LOCKS_ALLOWED_NO_WRAPPING, DefaultModality.NON_MODAL),

  UI(LockBehavior.LOCKS_DISALLOWED_FAIL_HARD, DefaultModality.NON_MODAL),

  MAIN(LockBehavior.LOCKS_ALLOWED_NO_WRAPPING, DefaultModality.ANY);

  enum class DefaultModality {
    ANY,
    NON_MODAL;

    fun toModalityState(): ModalityState = when (this) {
      ANY -> ModalityState.any()
      NON_MODAL -> ModalityState.nonModal()
    }
  }

  enum class LockBehavior {
    LOCKS_ALLOWED_MANDATORY_WRAPPING,
    LOCKS_ALLOWED_NO_WRAPPING,
    LOCKS_DISALLOWED_FAIL_HARD,
  }

  fun presentableName(): String = when (this) {
    EDT -> "Dispatchers.EDT"
    LAX_UI -> "Dispatchers.ui(RELAX)"
    UI -> "Dispatchers.UI"
    MAIN -> "Dispatchers.Main"
  }
}

private val ImmediateEdtDispatcher = ImmediateEdtCoroutineDispatcher(EdtDispatcherKind.EDT)
private val ImmediateLaxUiDispatcher = ImmediateEdtCoroutineDispatcher(EdtDispatcherKind.LAX_UI)
private val ImmediateUiDispatcher = ImmediateEdtCoroutineDispatcher(EdtDispatcherKind.UI)
private val ImmediateMainDispatcher = ImmediateEdtCoroutineDispatcher(EdtDispatcherKind.MAIN)