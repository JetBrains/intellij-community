// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.util.ui.EDT
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

internal sealed class EdtCoroutineDispatcher : MainCoroutineDispatcher() {

  override val immediate: MainCoroutineDispatcher get() = Immediate

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    val state = context[ModalityStateElement]?.modalityState
                ?: ModalityState.any()
    val runnable = if (state === ModalityState.any()) {
      block
    }
    else {
      DispatchedRunnable(context.job, block)
    }
    ApplicationManager.getApplication().invokeLater(runnable, state)
  }

  companion object : EdtCoroutineDispatcher() {

    override fun toString() = "EDT"
  }

  object Immediate : EdtCoroutineDispatcher() {

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
      if (!EDT.isCurrentThreadEdt()) {
        return true
      }
      // The current coroutine is executed with the correct modality state
      // (the execution would be postponed otherwise)
      // => there is no need to check modality state here.
      return false
    }

    override fun toString() = "EDT.immediate"
  }
}
