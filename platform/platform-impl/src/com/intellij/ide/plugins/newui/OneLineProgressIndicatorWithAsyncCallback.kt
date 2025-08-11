// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class OneLineProgressIndicatorWithAsyncCallback(private val coroutineScope: CoroutineScope, withText: Boolean, var callback: suspend () -> Unit) : OneLineProgressIndicator(withText) {
  override fun cancelRequest() {
    coroutineScope.launch {
      super.cancelRequest()
      callback()
    }
  }
}