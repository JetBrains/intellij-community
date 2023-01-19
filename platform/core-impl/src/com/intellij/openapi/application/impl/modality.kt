// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.coroutineContext

@Internal
suspend fun <T> onEdtInNonAnyModality(action: suspend CoroutineScope.() -> T): T = when {
  coroutineContext.contextModality() != ModalityState.any() -> {
    withContext(Dispatchers.EDT, action)
  }
  @OptIn(ExperimentalStdlibApi::class)
  coroutineContext[CoroutineDispatcher] != Dispatchers.EDT -> {
    withContext(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement(), action)
  }
  else -> {
    withContext(ModalityState.NON_MODAL.asContextElement()) {
      // Force re-dispatch because changing context modality without changing the dispatcher
      // continues the execution in the current EDT event.
      yield()
      action()
    }
  }
}

@Internal
fun <T> inModalContext(modalJob: JobProvider, action: (ModalityState) -> T): T {
  val newModalityState = LaterInvocator.getCurrentModalityState().appendEntity(modalJob)
  LaterInvocator.enterModal(modalJob, newModalityState)
  try {
    return action(newModalityState)
  }
  finally {
    LaterInvocator.leaveModal(modalJob)
  }
}
