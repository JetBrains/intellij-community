// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class MinimapHoverStateMachine(
  parentScope: CoroutineScope,
  private val panel: MinimapPanel,
  private val onStateChanged: (MinimapHoverTarget?) -> Unit
): Disposable {
  private val scope = parentScope.childScope("MinimapHoverStateMachine")
  private var activeTarget: MinimapHoverTarget? = null
  private var pendingTarget: MinimapHoverTarget? = null
  private var pendingActivationJob: Job? = null

  override fun dispose() {
    cancelPendingActivation()
    scope.cancel()
  }

  fun activeTarget(): MinimapHoverTarget? = activeTarget

  fun updateTarget(target: MinimapHoverTarget?, delay: Duration = INITIAL_HOVER_DELAY) {
    if (target == null) {
      cancelPendingActivation()
      deactivateNow()
      return
    }

    if (target.sameAs(activeTarget)) {
      cancelPendingActivation()
      return
    }
    if (target.sameAs(pendingTarget)) return

    pendingTarget = target
    pendingActivationJob?.cancel()
    pendingActivationJob = if (delay > Duration.ZERO) {
      scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        delay(delay)
        if (target.sameAs(pendingTarget)) {
          activate(target)
        }
      }
    }
    else {
      activate(target)
      null
    }
  }

  fun syncActiveTarget(target: MinimapHoverTarget?) {
    val current = activeTarget
    if (target == null) {
      cancelPendingActivation()
    }
    if (current == null && target == null) return
    if (current != null && target != null && current.sameAs(target)) {
      cancelPendingActivation()
      return
    }

    activeTarget = target
    onStateChanged(target)
    panel.repaint()
  }

  private fun activate(target: MinimapHoverTarget) {
    val current = activeTarget
    if (current != null && current.sameAs(target)) {
      pendingActivationJob = null
      pendingTarget = null
      return
    }

    pendingActivationJob = null
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

  private fun cancelPendingActivation() {
    pendingActivationJob?.cancel()
    pendingActivationJob = null
    pendingTarget = null
  }

  companion object {
    private val INITIAL_HOVER_DELAY: Duration = 60.milliseconds
  }
}
