// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.icons.Icon

class CoroutineBasedMutableIconUpdateFlow(
  private val coroutineScope: CoroutineScope,
  updateCallback: (Int) -> Unit
): MutableIconUpdateFlowBase(updateCallback) {
  override fun MutableSharedFlow<Int>.emitDelayed(delay: Long, value: Int) {
    coroutineScope.launch {
      delay(delay)
      if (handleRateLimiting()) return@launch
      emit(value)
      updateCallback(value)
    }
  }

  override fun collectDynamic(flow: Flow<Icon>, handler: (Icon) -> Unit) {
    coroutineScope.launch {
      flow.collect { handler(it) }
    }
  }
}