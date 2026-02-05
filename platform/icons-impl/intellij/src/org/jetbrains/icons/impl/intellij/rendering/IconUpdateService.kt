// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.InternalIconsApi

@Service(Service.Level.APP)
@InternalIconsApi
class IconUpdateService(val scope: CoroutineScope) {
  @ApiStatus.Experimental
  fun scheduleDelayedUpdate(delay: Long, updateId: Int, flow: MutableSharedFlow<Int>, updateCallback: (Int) -> Unit, rateLimiter: () -> Boolean = { false }) {
    scope.launch {
      delay(delay)
      if (rateLimiter()) return@launch
      flow.emit(updateId)
      updateCallback(updateId)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): IconUpdateService = service<IconUpdateService>()
  }
}