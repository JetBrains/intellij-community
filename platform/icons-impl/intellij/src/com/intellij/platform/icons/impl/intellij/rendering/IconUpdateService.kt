// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.APP)
@ApiStatus.Internal
class IconUpdateService(val scope: CoroutineScope) {
  fun scheduleDelayedUpdate(delay: Long, updateId: Int, flow: MutableSharedFlow<Int>, onUpdate: (suspend (Int) -> Unit)? = null, rateLimiter: () -> Boolean = { false }) {
    scope.launch {
      if (delay != 0L) {
        delay(delay.milliseconds)
        if (rateLimiter()) return@launch
      }
      flow.emit(updateId)
      onUpdate?.invoke(updateId)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): IconUpdateService = service<IconUpdateService>()
  }
}