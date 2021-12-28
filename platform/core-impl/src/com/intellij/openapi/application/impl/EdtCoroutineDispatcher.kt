// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

internal object EdtCoroutineDispatcher : CoroutineDispatcher() {

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    val state = context[ModalityStateElement]?.modalityState ?: ModalityState.any()
    ApplicationManager.getApplication().invokeLater(block, state)
  }
}
