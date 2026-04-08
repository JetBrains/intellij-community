// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.geometry.MinimapScaleUtil
import com.intellij.ide.minimap.hover.MinimapHoverController
import com.intellij.ide.minimap.interaction.MinimapMouseInteractionController
import com.intellij.ide.minimap.layers.MinimapLayerFactory
import com.intellij.ide.minimap.layers.MinimapLayerPipeline
import com.intellij.ide.minimap.layers.MinimapLayerRenderState
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.ide.minimap.settings.MinimapSettings
import com.intellij.ide.minimap.settings.MinimapSettingsState
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.util.Disposer
import com.intellij.ui.PopupHandler
import kotlinx.coroutines.CoroutineScope
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel

class MinimapPanel(
  coroutineScope: CoroutineScope,
  val editor: Editor,
  val container: JPanel,
) : JPanel(), Disposable {
  val settings: MinimapSettings = MinimapSettings.getInstance()

  private val settingsState: MinimapSettingsState
    get() = settings.state

  private var snapshot: MinimapSnapshot? = null

  private var initialized = false

  @Volatile
  private var disposed = false

  private val minimapController = MinimapController(
    coroutineScope,
    this,
    container,
  ).also { Disposer.register(this, it) }

  private val hoverController = MinimapHoverController(
    coroutineScope,
    this,
    minimapController::isDocumentCommitted,
  ).also { Disposer.register(this, it) }

  private val interactionController = MinimapMouseInteractionController(
    this,
    hoverController,
  ).also { Disposer.register(this, it) }

  internal val layerPainter = MinimapLayerPainter(
    editor = editor,
    minimapController = minimapController,
    hoverController = hoverController,
    repaintRequest = ::repaint,
  )

  private val layerPipeline = MinimapLayerPipeline(
    layers = MinimapLayerFactory.createLayers(this),
  )

  private val onSettingsChange = { _: MinimapSettings.SettingsChangeType ->
    updatePreferredSize()
    revalidate()
    minimapController.refreshSnapshot()
    repaint()
  }

  init {
    // Tie the panel's lifetime to the editor: when the editor is closed,
    // this panel (and all its children) are disposed automatically.
    EditorUtil.disposeWithEditor(editor, this)

    PopupHandler.installPopupMenu(this, createPopupActionGroup(), "MinimapPopup")
    installSettingsListeners()
    updatePreferredSize()

    if (!MinimapRegistry.isLegacy()) {
      minimapController.updateStructureMarkersNow()
    }

    minimapController.install()
    interactionController.install()
  }

  // Called by Disposer after all children (controllers) have been disposed.
  // Settings listener is removed here — not earlier — so it cannot fire
  // after the panel is gone but before the controllers are cleaned up.
  override fun dispose() {
    disposed = true
    uninstallSettingsListeners()
    layerPainter.clear()
    snapshot = null
    container.remove(this)
    container.revalidate()
    container.repaint()
  }

  fun onClose() {
    if (!disposed) {
      Disposer.dispose(this)
    }
  }

  override fun removeNotify() {
    if (!disposed) {
      hoverController.hideBalloon()
    }
    super.removeNotify()
  }

  override fun paint(g: Graphics) {
    if (!initialized) {
      minimapController.refreshSnapshot()
      initialized = true
    }

    val g2d = g as Graphics2D
    g2d.color = editor.contentComponent.background
    g2d.fillRect(0, 0, width, height)

    val snapshot = currentSnapshot() ?: return
    val layerState = MinimapLayerRenderState(
      snapshot = snapshot,
      panelWidth = width,
      isLegacyMode = MinimapRegistry.isLegacy(),
    )
    layerPipeline.paint(g2d, layerState)
  }

  override fun updateUI() {
    super.updateUI()

    if (initialized && MinimapRegistry.isLegacy()) {
      layerPainter.updateLegacyPreview(currentSnapshot()?.geometry?.minimapHeight ?: 0)
    }
  }

  fun scrollTo(y: Int) {
    val geometry = currentSnapshot()?.geometry ?: return
    val targetScrollOffset = MinimapScrollUtil.targetScrollOffsetForPoint(
      y = y,
      geometry = geometry,
      contentHeight = editor.contentComponent.size.height,
      viewportHeight = editor.component.size.height,
    ) ?: return
    editor.scrollingModel.scrollVertically(targetScrollOffset)
  }

  fun scrollThumbTo(y: Int, dragOffset: Int) {
    val geometry = currentSnapshot()?.geometry ?: return
    val targetScrollOffset = MinimapScrollUtil.targetScrollOffsetForThumbDrag(
      y = y,
      dragOffset = dragOffset,
      panelHeight = height,
      geometry = geometry,
      contentHeight = contentHeight(),
      visibleHeight = editor.scrollingModel.visibleArea.height,
    ) ?: return
    editor.scrollingModel.scrollVertically(targetScrollOffset)
  }

  fun currentSnapshot(): MinimapSnapshot? = snapshot

  fun updateSnapshot(snapshot: MinimapSnapshot) {
    this.snapshot = snapshot
    hoverController.onSnapshot(snapshot)
  }

  internal fun updatePreferredWidth(preferredWidth: Int): Boolean {
    if (preferredSize.width == preferredWidth) return false
    preferredSize = Dimension(preferredWidth, 0)
    return true
  }

  private fun updatePreferredSize() {
    val panelHeight = if (height > 0) height else container.height
    val preferredWidth = MinimapScaleUtil.effectiveWidth(editor, panelHeight, settingsState.width, settingsState.scaleMode)
    updatePreferredWidth(preferredWidth)
  }

  private fun contentHeight(): Int {
    val projectedLineCount = currentSnapshot()?.context?.lineProjection?.projectedLineCount ?: editor.document.lineCount
    return MinimapScaleUtil.contentHeight(editor, projectedLineCount)
  }

  private fun installSettingsListeners() {
    settings.settingsChangeCallback += onSettingsChange
  }

  private fun uninstallSettingsListeners() {
    settings.settingsChangeCallback -= onSettingsChange
  }

  private fun createPopupActionGroup() = CustomActionsSchema.getInstance().getCorrectedAction("MinimapActionsGroup") as? ActionGroup
                                         ?: DefaultActionGroup()
}
