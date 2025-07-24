// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.CoroutineSupport
import com.intellij.openapi.application.CoroutineSupport.UiDispatcherKind
import com.intellij.openapi.application.CoroutineSupport.UiDispatcherKind.*
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
open class PlatformCoroutineSupport() : CoroutineSupport {
  final override fun uiDispatcher(kind: UiDispatcherKind, immediate: Boolean): CoroutineContext {
    val mainDispatcher = when (kind) {
      STRICT -> EdtCoroutineDispatcher.LockForbidden
      RELAX -> EdtCoroutineDispatcher.NonLocking
      LEGACY -> {
        warnAboutUsingLegacyEdt()
        EdtCoroutineDispatcher.LockWrapping
      }
    }
    return if (immediate) {
      mainDispatcher.immediate
    }
    else {
      mainDispatcher
    }
  }

  // we cannot use modern StackWalker API - limited to Java 8
  protected open fun warnAboutUsingLegacyEdt() {
  }
}