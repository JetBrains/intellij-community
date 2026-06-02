// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.interaction.MinimapInteractionPolicy
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.awt.Graphics2D
import java.awt.Point
import kotlin.time.Duration

class MinimapHoverController(
  coroutineScope: CoroutineScope,
  private val panel: MinimapPanel,
  private val isDocumentCommitted: () -> Boolean,
): Disposable {
  private val scope = coroutineScope.childScope("MinimapHoverController")
  private val hitChecker = MinimapHoverHitCheck(panel.editor)
  private val presenter = MinimapHoverPresenter(panel)
  private val hoverPolicy = MinimapInteractionPolicy.forEditor(panel.editor)
  private val settings = MinimapSettings.getInstance()
  private var lastSnapshot: MinimapSnapshot? = null
  private var lastMousePoint: Point? = null
  private var delayNextHover = true
  private var dragging = false
  private var hoverEnabled = true

  private val hoverStateMachine = MinimapHoverStateMachine(scope, panel) { target ->
    presenter.setTarget(target)
  }.also {
    Disposer.register(this, it)
  }

  override fun dispose() {
    presenter.hide()
    lastSnapshot = null
    lastMousePoint = null
    scope.cancel()
  }

  fun onSnapshot(snapshot: MinimapSnapshot) {
    hoverEnabled = settings.state.showHover && hoverPolicy.isHoverEnabled(panel.editor, snapshot)
    if (!hoverEnabled) {
      hideBalloon()
    }
    lastSnapshot = snapshot
    presenter.setContext(snapshot.context)
    if (hoverEnabled) {
      if (dragging) {
        updateActiveTargetForSnapshot(snapshot)
        return
      }
      val point = lastMousePoint
      if (point != null) {
        updateActiveTargetForPoint(snapshot, point)
        return
      }
      updateActiveTargetForSnapshot(snapshot)
    }
  }

  fun paint(graphics: Graphics2D) {
    if (!hoverEnabled) return
    presenter.paint(graphics)
  }

  fun hideBalloon() {
    hoverStateMachine.updateTarget(null)
    hoverStateMachine.syncActiveTarget(null)
    presenter.hide()
  }

  fun onMouseEntered() {
    delayNextHover = true
  }

  fun onMouseExited() {
    if (dragging) {
      lastMousePoint = null
      delayNextHover = true
      return
    }
    updateHover(null)
  }

  fun onScroll(point: Point?) {
    point?.let {
      lastMousePoint = Point(it)
      delayNextHover = false
    }
    if (!hoverEnabled || dragging) return
    val snapshot = lastSnapshot ?: return
    val lastPoint = lastMousePoint ?: return
    updateActiveTargetForPoint(snapshot, lastPoint)
  }

  fun startDragging() {
    dragging = true
  }

  fun stopDragging(point: Point?) {
    dragging = false
    updateHover(point)
  }

  fun updateHover(point: Point?) {
    if (point == null) {
      lastMousePoint = null
      delayNextHover = true
      hoverStateMachine.updateTarget(null)
      return
    }

    val hoverDelay = if (delayNextHover && hoverStateMachine.activeTarget() == null) null else Duration.ZERO
    delayNextHover = false
    lastMousePoint = Point(point)

    if (!hoverEnabled) return

    val snapshot = lastSnapshot ?: run {
      hoverStateMachine.updateTarget(null)
      return
    }

    updateTargetForPoint(snapshot, point, hoverDelay)
  }

  private fun updateTargetForPoint(snapshot: MinimapSnapshot, point: Point, delay: Duration?) {
    if (!isDocumentCommitted() || snapshot.structureEntries.isEmpty()) {
      hoverStateMachine.updateTarget(null)
      return
    }

    val target = computeHoverTarget(snapshot, point) ?: run {
      hoverStateMachine.updateTarget(null)
      return
    }

    if (delay == null) {
      hoverStateMachine.updateTarget(target)
    }
    else {
      hoverStateMachine.updateTarget(target, delay)
    }
  }

  private fun computeHoverTarget(snapshot: MinimapSnapshot, point: Point): MinimapHoverTarget? {
    val hit = hitChecker.hitCheck(snapshot, point) ?: return null
    val text = hit.text ?: return null

    return MinimapHoverTarget(hit.entry, hit.rect, text, hit.icon, hit.declarationWidth)
  }

  private fun updateActiveTargetForPoint(snapshot: MinimapSnapshot, point: Point) {
    val active = hoverStateMachine.activeTarget() ?: return
    val target = computeHoverTarget(snapshot, point) ?: run {
      hoverStateMachine.updateTarget(null)
      return
    }

    if (!target.entry.isSameEntry(active.entry)) {
      hoverStateMachine.updateTarget(null)
      return
    }

    hoverStateMachine.syncActiveTarget(target)
  }

  private fun updateActiveTargetForSnapshot(snapshot: MinimapSnapshot) {
    if (!hoverEnabled) return

    if (!isDocumentCommitted() || snapshot.structureEntries.isEmpty()) {
      hoverStateMachine.updateTarget(null)
      return
    }

    val active = hoverStateMachine.activeTarget() ?: return

    val updatedEntry = snapshot.structureEntries.firstOrNull { it.isSameEntry(active.entry) } ?: run {
      hoverStateMachine.updateTarget(null)
      return
    }

    val updatedRect = hitChecker.computeHoverRect(updatedEntry, snapshot.context) ?: run {
      hoverStateMachine.updateTarget(null)
      return
    }

    val updatedDeclarationWidth = hitChecker.computeDeclarationWidth(updatedEntry, snapshot.context, snapshot.layoutMetrics)
    hoverStateMachine.syncActiveTarget(active.copy(entry = updatedEntry, rect = updatedRect, declarationWidth = updatedDeclarationWidth))
  }
}
