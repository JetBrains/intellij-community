// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.isRunBlockingUnderReadAction
import com.intellij.openapi.util.Conditions
import com.intellij.util.ui.EDT
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

/**
 * Default UI dispatcher, it dispatches within the [context modality state][com.intellij.openapi.application.asContextElement].
 *
 * Cancelled coroutines are dispatched [without modality state][ModalityState.any] to be able to finish fast.
 * Such coroutines are not expected to access the model (PSI/VFS/etc).
 *
 * This dispatcher is installed as [main][kotlinx.coroutines.Dispatchers.Main].
 */
internal sealed class EdtCoroutineDispatcher : MainCoroutineDispatcher() {

  override val immediate: MainCoroutineDispatcher get() = Immediate

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    check(!context.isRunBlockingUnderReadAction()) {
      "Switching to Dispatchers.EDT from `runBlockingCancellable` inside in a read-action leads to possible deadlock."
    }
    val state = context.effectiveContextModality()
    val runnable = if (state === ModalityState.any()) {
      ContextAwareRunnable(block::run)
    }
    else {
      DispatchedRunnable(context.job, block)
    }
    ApplicationManagerEx.getApplicationEx().dispatchCoroutineOnEDT(runnable, state)
  }

  companion object : EdtCoroutineDispatcher() {

    override fun toString(): String = "Dispatchers.EDT"
  }

  object Immediate : EdtCoroutineDispatcher() {

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
      // The current coroutine is executed with the correct modality state
      // (the execution would be postponed otherwise).
      // But the code that's about to be executed may belong to a different coroutine
      // (e.g., one coroutine emits a value into a flow and another collects it).
      // If the context modality is lower than the current modality,
      // we need to dispatch and postpone its execution.
      if (!EDT.isCurrentThreadEdt()) return true
      val contextModality = context.effectiveContextModality()
      // If the context modality is explicitly any(), then no dispatch is performed,
      // as dominates(any()) always returns false, no special any() handling required here.
      return ModalityState.current().dominates(contextModality)
    }

    override fun toString(): String = "Dispatchers.EDT.immediate"
  }
}

private fun CoroutineContext.effectiveContextModality(): ModalityState =
  contextModality() ?: ModalityState.nonModal() // dispatch with NON_MODAL by default
