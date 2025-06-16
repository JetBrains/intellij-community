// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.CoroutineSupport
import com.intellij.openapi.application.UiDispatcherKind
import com.intellij.openapi.application.UiDispatcherKind.*
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext

@Internal
internal class PlatformCoroutineSupport : CoroutineSupport {

  override fun uiDispatcher(kind: UiDispatcherKind, immediate: Boolean): CoroutineContext {
    val mainDispatcher = when (kind) {
      STRICT -> EdtCoroutineDispatcher.LockForbidden
      RELAX -> EdtCoroutineDispatcher.NonLocking
      LEGACY -> EdtCoroutineDispatcher.LockWrapping
    }
    return if (immediate) {
      mainDispatcher.immediate
    } else {
      mainDispatcher
    }
  }
}
