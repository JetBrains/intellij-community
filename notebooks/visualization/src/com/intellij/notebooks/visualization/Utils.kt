// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization

import com.intellij.openapi.application.EDT
import com.intellij.util.ui.EDT.isCurrentThreadEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun performOnEdtThread(block: () -> Unit) {
  if (isCurrentThreadEdt()) {
    block()
  } else withContext(Dispatchers.EDT) {
    block()
  }
}