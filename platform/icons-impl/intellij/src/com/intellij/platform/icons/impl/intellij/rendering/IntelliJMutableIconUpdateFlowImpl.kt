// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.impl.rendering.MutableIconUpdateFlowBase

internal class IntelliJMutableIconUpdateFlowImpl(
  val onUpdate: (suspend (Int) -> Unit)? = null
) : MutableIconUpdateFlowBase() {
  override fun MutableSharedFlow<Int>.emitDelayed(delay: Long, value: Int) {
    IconUpdateService.getInstance().scheduleDelayedUpdate(delay, value, this@emitDelayed, onUpdate) {
      handleRateLimiting()
    }
  }

  override fun triggerUpdate() {
    if (onUpdate != null) {
      triggerDelayedUpdate(0L)
    } else super.triggerUpdate()
  }

  override fun collectDynamic(flow: Flow<Icon>, handler: (Icon) -> Unit) {
    IconUpdateService.getInstance().scope.launch {
      flow.collect { handler(it) }
    }
  }
}