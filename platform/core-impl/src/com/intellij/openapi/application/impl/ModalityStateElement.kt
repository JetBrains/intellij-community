// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ModalityState
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal class ModalityStateElement(
  val modalityState: ModalityState,
) : AbstractCoroutineContextElement(ModalityStateElement) {

  companion object : CoroutineContext.Key<ModalityStateElement>
}

internal fun CoroutineContext.contextModality(): ModalityState {
  return this[ModalityStateElement]?.modalityState
         ?: ModalityState.any()
}

internal suspend fun contextModality(): ModalityState {
  return coroutineContext.contextModality()
}
