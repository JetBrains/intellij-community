// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.CoroutineSupport
import com.intellij.openapi.application.ModalityState
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext

@Internal
internal class PlatformCoroutineSupport : CoroutineSupport {

  override fun asContextElement(modalityState: ModalityState): CoroutineContext {
    return ModalityStateElement(modalityState)
  }

  override fun edtDispatcher(): CoroutineContext {
    return EdtCoroutineDispatcher
  }
}
