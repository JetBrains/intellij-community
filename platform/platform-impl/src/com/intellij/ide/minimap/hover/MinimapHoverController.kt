// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.hover

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.ide.minimap.layout.MinimapLayoutMode
import java.awt.Graphics2D
import java.awt.Point

class MinimapHoverController(
  coroutineScope: CoroutineScope,
  panel: MinimapPanel,
  private val isDocumentCommitted: () -> Boolean,
): Disposable {
  private val scope = coroutineScope.childScope("MinimapHoverController")
  private val hitChecker = MinimapHoverHitCheck(panel.editor)
  private val presenter = MinimapHoverPresenter(panel)
  private var lastSnapshot: MinimapSnapshot? = null
  private var hoverEnabled = true

  private val hoverStateMachine = MinimapHoverStateMachine(scope, panel) { target ->
    presenter.setTarget(target)
  }.also {
    Disposer.register(this, it)
  }

  init {
    hoverStateMachine.start()
  }

  override fun dispose() {
    presenter.hide()
    lastSnapshot = null
    scope.cancel()
  }

  fun onSnapshot(snapshot: MinimapSnapshot) {
    hoverEnabled = snapshot.layoutMode == MinimapLayoutMode.EXACT
    if (!hoverEnabled) {
      hideBalloon()
    }
    lastSnapshot = snapshot
    presenter.setContext(snapshot.context)
    if (hoverEnabled) {
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

  fun updateHover(point: Point?) {
    if (!hoverEnabled) return
    if (point == null) {
      hoverStateMachine.updateTarget(null)
      return
    }

    val snapshot = lastSnapshot ?: run {
      hoverStateMachine.updateTarget(null)
      return
    }

    if (!isDocumentCommitted() || snapshot.structureEntries.isEmpty()) {
      hoverStateMachine.updateTarget(null)
      return
    }

    val hit = hitChecker.hitCheck(snapshot, point) ?: run {
      hoverStateMachine.updateTarget(null)
      return
    }

    val text = hit.text ?: run {
      hoverStateMachine.updateTarget(null)
      return
    }

    hoverStateMachine.updateTarget(MinimapHoverTarget(hit.entry, hit.rect, text, hit.icon))
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

    hoverStateMachine.syncActiveTarget(active.copy(entry = updatedEntry, rect = updatedRect))
  }
}
