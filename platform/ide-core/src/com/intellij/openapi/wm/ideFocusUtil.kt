// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.contextModality
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun IdeFocusManager.awaitFocusSettlesDown() {
  suspendCancellableCoroutine<Unit> {
    doWhenFocusSettlesDown(
      {
        it.resume(Unit)
      }, it.context.contextModality() ?: ModalityState.nonModal())
  }
}

