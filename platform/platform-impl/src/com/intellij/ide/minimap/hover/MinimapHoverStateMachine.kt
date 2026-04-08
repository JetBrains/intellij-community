// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.openapi.Disposable
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
internal class MinimapHoverStateMachine(
  parentScope: CoroutineScope,
  private val panel: MinimapPanel,
  private val onStateChanged: (MinimapHoverTarget?) -> Unit
): Disposable {
  private val scope = parentScope.childScope("MinimapHoverStateMachine")
  private val hoverEvents = MutableSharedFlow<MinimapHoverEvent>(extraBufferCapacity = 1)
  private var activeTarget: MinimapHoverTarget? = null

  override fun dispose() = scope.cancel()

  fun activeTarget(): MinimapHoverTarget? = activeTarget

  fun start(): Job = scope.launch {
    hoverEvents.filterIsInstance<MinimapHoverEvent.TargetChanged>()
      .transformLatest { event ->
        delay(HOVER_DELAY_MS)
        emit(event)
      }
      .collect { event ->
        val target = event.target
        if (target == null) {
          deactivateNow()
        }
        else {
          activate(target)
        }
      }
  }

  fun updateTarget(target: MinimapHoverTarget?) {
    hoverEvents.tryEmit(MinimapHoverEvent.TargetChanged(target))
  }

  fun syncActiveTarget(target: MinimapHoverTarget?) {
    val current = activeTarget
    if (current == null && target == null) return
    if (current != null && target != null && current.sameAs(target)) return

    activeTarget = target
    onStateChanged(target)
    panel.repaint()
  }

  private fun activate(target: MinimapHoverTarget) {
    val current = activeTarget
    if (current != null && current.sameAs(target)) return

    activeTarget = target
    onStateChanged(activeTarget)
    panel.repaint()
  }

  private fun deactivateNow() {
    if (activeTarget == null) return
    activeTarget = null
    onStateChanged(null)
    panel.repaint()
  }

  companion object {
    private const val HOVER_DELAY_MS: Long = 125
  }
}
