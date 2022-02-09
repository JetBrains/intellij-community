// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ModalityState
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class ModalityStateElement(
  val modalityState: ModalityState,
) : AbstractCoroutineContextElement(ModalityStateElement) {

  companion object : CoroutineContext.Key<ModalityStateElement>
}
