// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.contextModality
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.ContinuationInterceptor
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
fun <T> inModalContext(modalJob: Job, action: (ModalityState) -> T): T {
  val newModalityState = LaterInvocator.getCurrentModalityState().appendEntity(modalJob)
  LaterInvocator.enterModal(modalJob, newModalityState)
  try {
    return action(newModalityState)
  }
  finally {
    LaterInvocator.leaveModal(modalJob)
  }
}

@Internal
suspend fun <X> withModalContext(
  action: suspend CoroutineScope.() -> X,
): X = coroutineScope {
  val originalDispatcher = requireNotNull(coroutineContext[ContinuationInterceptor])
  val contextModality = coroutineContext.contextModality()
  if (Dispatchers.EDT === originalDispatcher) {
    if (contextModality == null || contextModality == ModalityState.any()) {
      // Force NON_MODAL, otherwise another modality could be entered concurrently.
      withContext(ModalityState.NON_MODAL.asContextElement()) {
        yield() // Force re-dispatch in the proper modality.
        withModalContextEDT(action)
      }
    }
    else {
      withModalContextEDT(action)
    }
  }
  else {
    val enterModalModality = if (contextModality == null || contextModality == ModalityState.any()) {
      ModalityState.NON_MODAL
    }
    else {
      contextModality
    }
    withContext(Dispatchers.EDT + enterModalModality.asContextElement()) {
      withModalContextEDT {
        withContext(originalDispatcher, action)
      }
    }
  }
}

private suspend fun <X> withModalContextEDT(action: suspend CoroutineScope.() -> X): X {
  val ctx = coroutineContext
  val job = ctx.job
  val newModalityState = (ctx.contextModality() as ModalityStateEx).appendJob(job) as ModalityStateEx
  LaterInvocator.enterModal(job, newModalityState)
  try {
    return withContext(newModalityState.asContextElement(), action)
  }
  finally {
    LaterInvocator.leaveModal(job)
  }
}
