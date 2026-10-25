// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.APP)
@ApiStatus.Internal
class IconUpdateService(val scope: CoroutineScope) {
  private val lafChangesFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  /**
   * Emits after each LaF change (`LafManagerListener`). The theme inputs of the icons are set at that moment. A Compose
   * icon caches its drawing, so it does not paint again by itself. It collects its update flow, which includes these
   * events, and paints again. The paint sees the new theme digest and rebuilds the renderers once.
   */
  val lafChanges: SharedFlow<Unit> = lafChangesFlow.asSharedFlow()

  init {
    ApplicationManager.getApplication().messageBus.connect(scope).subscribe(LafManagerListener.TOPIC, LafManagerListener {
      lafChangesFlow.tryEmit(Unit)
    })
  }

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
