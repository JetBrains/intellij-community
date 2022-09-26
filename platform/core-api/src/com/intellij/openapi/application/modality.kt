// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The code within [ModalityState.any] context modality state must only perform pure UI operations,
 * it must not access any PSI, VFS, project model, or indexes. It also must not show any modal dialogs.
 */
@Suppress("CONFLICTING_OVERLOADS")
fun ModalityState.asContextElement(): CoroutineContext {
  return ModalityStateElement(this)
}

@Internal
fun CoroutineContext.contextModality(): ModalityState? {
  return this[ModalityStateElementKey]?.modalityState
}

private object ModalityStateElementKey
  : CoroutineContext.Key<ModalityStateElement>

private class ModalityStateElement(val modalityState: ModalityState)
  : AbstractCoroutineContextElement(ModalityStateElementKey) {

  override fun toString(): String {
    return modalityState.toString()
  }
}
