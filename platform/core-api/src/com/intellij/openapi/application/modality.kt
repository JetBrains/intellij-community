// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.concurrency.currentThreadContext
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The code within [ModalityState.any] context modality state must only perform pure UI operations,
 * it must not access any PSI, VFS, project model, or indexes. It also must not show any modal dialogs.
 */
suspend fun isModalAwareContext(): Boolean {
  return currentCoroutineContext().contextModality() != ModalityState.any()
}

@Suppress("CONFLICTING_OVERLOADS") // KT-61878
fun ModalityState.asContextElement(): CoroutineContext {
  return ModalityStateElement(this)
}

@Internal
fun CoroutineContext.contextModality(): ModalityState? {
  return this[ModalityStateElementKey]?.modalityState
}

@Internal
fun currentThreadContextModality(): ModalityState? {
  return currentThreadContext().contextModality()
}

private object ModalityStateElementKey
  : CoroutineContext.Key<ModalityStateElement>

private class ModalityStateElement(val modalityState: ModalityState)
  : AbstractCoroutineContextElement(ModalityStateElementKey) {

  override fun toString(): String {
    return modalityState.toString()
  }
}
