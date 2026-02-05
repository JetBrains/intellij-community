// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.icons.Icon
import org.jetbrains.icons.impl.rendering.MutableIconUpdateFlowBase

internal class IntelliJMutableIconUpdateFlowImpl(
  updateCallback: (Int) -> Unit
) : MutableIconUpdateFlowBase(updateCallback) {
  override fun MutableSharedFlow<Int>.emitDelayed(delay: Long, value: Int) {
    IconUpdateService.getInstance().scheduleDelayedUpdate(delay, value, this@emitDelayed, updateCallback) {
      handleRateLimiting()
    }
  }

  override fun collectDynamic(flow: Flow<Icon>, handler: (Icon) -> Unit) {
    IconUpdateService.getInstance().scope.launch {
      flow.collect { handler(it) }
    }
  }
}