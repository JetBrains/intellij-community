// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.interaction

import com.intellij.ide.minimap.MinimapPanel
import com.intellij.ide.minimap.MinimapUsageCollector
import com.intellij.ide.minimap.layout.MinimapLayoutMode
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseEvent

/**
 * Controls minimap interaction behavior for an editor: structure element filtering, hover display,
 * mouse events, and independent scroll.
 *
 * Register via `com.intellij.minimapInteractionPolicy`.
 * For structure markers, hover, and scroll, the first applicable policy wins.
 * For mouse events, applicable handlers are evaluated in registration order; the first handler that returns `true` wins.
 */
@ApiStatus.OverrideOnly
interface MinimapInteractionPolicy {
  fun isApplicable(editor: Editor): Boolean = true

  /**
   * Returns whether the given structure view element should appear as a hover marker in the minimap.
   */
  fun isRelevantStructureElement(element: StructureViewTreeElement, value: Any): Boolean = true

  fun isHoverEnabled(editor: Editor, snapshot: MinimapSnapshot): Boolean =
    snapshot.layoutMode == MinimapLayoutMode.EXACT

  fun handleClick(panel: MinimapPanel, event: MouseEvent): Boolean = false

  fun handleMouseMoved(panel: MinimapPanel, event: MouseEvent): Boolean = false

  fun handleMouseExited(panel: MinimapPanel, event: MouseEvent): Boolean = false

  fun useIndependentMinimapScroll(editor: Editor): Boolean = false

  /**
   * Returns whether generic `editor.minimap` interaction events should be logged for this editor.
   * Policies that report domain-specific minimap interactions can disable the generic collector.
   */
  fun isGenericInteractionLoggingEnabled(editor: Editor): Boolean = true

  /**
   * Multiplier applied to wheel rotation when scrolling the independent minimap viewport.
   * Lower values produce slower, less sensitive scrolling.
   */
  fun independentWheelSensitivity(): Double = 1000.0

  fun onWheelScrolled(
    panel: MinimapPanel,
    direction: MinimapUsageCollector.ScrollDirection,
  ) { }

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<MinimapInteractionPolicy> =
      ExtensionPointName("com.intellij.minimapInteractionPolicy")

    fun forEditor(editor: Editor): MinimapInteractionPolicy {
      return EP_NAME.extensionList.firstOrNull { it.isApplicable(editor) } ?: DefaultMinimapInteractionPolicy
    }

    fun handleClick(panel: MinimapPanel, event: MouseEvent): Boolean {
      val editor = panel.editor
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        if (policy.handleClick(panel, event)) return true
      }
      return false
    }

    fun handleMouseMoved(panel: MinimapPanel, event: MouseEvent): Boolean {
      val editor = panel.editor
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        if (policy.handleMouseMoved(panel, event)) return true
      }
      return false
    }

    fun handleMouseExited(panel: MinimapPanel, event: MouseEvent): Boolean {
      val editor = panel.editor
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        if (policy.handleMouseExited(panel, event)) return true
      }
      return false
    }

    fun useIndependentMinimapScroll(editor: Editor): Boolean {
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        return policy.useIndependentMinimapScroll(editor)
      }
      return false
    }

    fun isGenericInteractionLoggingEnabled(editor: Editor): Boolean {
      for (policy in EP_NAME.extensionList) {
        if (!policy.isApplicable(editor)) continue
        return policy.isGenericInteractionLoggingEnabled(editor)
      }
      return true
    }
  }
}
