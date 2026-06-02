// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.openapi.Disposable
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class MinimapHoverStateMachine(
  parentScope: CoroutineScope,
  private val panel: MinimapPanel,
  private val onStateChanged: (MinimapHoverTarget?) -> Unit
): Disposable {
  private val scope = parentScope.childScope("MinimapHoverStateMachine")
  private val hoverEvents = MutableSharedFlow<MinimapHoverEvent>(extraBufferCapacity = 1)
  private var activeTarget: MinimapHoverTarget? = null
  private var pendingTarget: MinimapHoverTarget? = null

  override fun dispose() = scope.cancel()

  fun activeTarget(): MinimapHoverTarget? = activeTarget

  fun start(): Job = scope.launch {
    hoverEvents.filterIsInstance<MinimapHoverEvent.TargetChanged>()
      .transformLatest { event ->
        if (event.delay > Duration.ZERO) {
          delay(event.delay)
        }
        emit(event)
      }
      .collect { event ->
        pendingTarget = null
        val target = event.target
        if (target == null) {
          deactivateNow()
        }
        else {
          activate(target)
        }
      }
  }

  fun updateTarget(target: MinimapHoverTarget?, delay: Duration = INITIAL_HOVER_DELAY) {
    if (target == null) {
      if (activeTarget == null && pendingTarget == null) return
      pendingTarget = null
      hoverEvents.tryEmit(MinimapHoverEvent.TargetChanged(null, Duration.ZERO))
      return
    }

    if (target.sameAs(activeTarget) || target.sameAs(pendingTarget)) return

    pendingTarget = target
    hoverEvents.tryEmit(MinimapHoverEvent.TargetChanged(target, delay))
  }

  fun syncActiveTarget(target: MinimapHoverTarget?) {
    val current = activeTarget
    if (target == null) {
      pendingTarget = null
    }
    if (current == null && target == null) return
    if (current != null && target != null && current.sameAs(target)) return

    activeTarget = target
    onStateChanged(target)
    panel.repaint()
  }

  private fun activate(target: MinimapHoverTarget) {
    val current = activeTarget
    if (current != null && current.sameAs(target)) return

    pendingTarget = null
    activeTarget = target
    onStateChanged(activeTarget)
    panel.repaint()
  }

  private fun deactivateNow() {
    pendingTarget = null
    if (activeTarget == null) return
    activeTarget = null
    onStateChanged(null)
    panel.repaint()
  }

  companion object {
    private val INITIAL_HOVER_DELAY: Duration = 60.milliseconds
  }
}
