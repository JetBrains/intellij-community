// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.PowerSaveMode
import com.intellij.ide.RemoteDesktopService
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import java.awt.Component
import java.awt.Container
import java.awt.Window
import javax.swing.*

internal object ScrollSettings {
  @JvmStatic
  fun isEligibleFor(component: Component?): Boolean {
    if (component == null || !component.isShowing || !LoadingState.COMPONENTS_REGISTERED.isOccurred) {
      return false
    }

    val app = ApplicationManager.getApplication()
    if (app == null || PowerSaveMode.isEnabled() || RemoteDesktopService.isRemoteSession()) {
      return false
    }

    val settings = UISettings.instanceOrNull
    return settings != null && settings.smoothScrolling
  }

  @JvmField
  val isHighPrecisionEnabled = Registry.booleanValueHotSupplier("idea.true.smooth.scrolling.high.precision", true)

  @JvmField
  val isPixelPerfectEnabled = Registry.booleanValueHotSupplier("idea.true.smooth.scrolling.pixel.perfect", true)

  @JvmField
  val isDebugEnabled = Registry.booleanValueHotSupplier("idea.true.smooth.scrolling.debug", false)

  @JvmField
  val isBackgroundFromView = Registry.booleanValueHotSupplier("ide.scroll.background.auto", true)

  private val scrollHeaderOverCornerEnabled = Registry.booleanValueHotSupplier("ide.scroll.layout.header.over.corner", true)

  @JvmStatic
  fun isHeaderOverCorner(viewport: JViewport?): Boolean {
    return !isNotSupportedYet(viewport?.view) && scrollHeaderOverCornerEnabled()
  }

  @JvmStatic
  fun isNotSupportedYet(view: Component?): Boolean = view is JTable

  @JvmField
  val isGapNeededForAnyComponent = Registry.booleanValueHotSupplier("ide.scroll.align.component", true)

  @JvmField
  val isHorizontalGapNeededOnMac = Registry.booleanValueHotSupplier("mac.scroll.horizontal.gap", false)

  @JvmField
  val isThumbSmallIfOpaque = Registry.booleanValueHotSupplier("ide.scroll.thumb.small.if.opaque", false)

  /* A heuristic that disables scrolling interpolation in diff / merge windows.
  We need to make scrolling synchronization compatible with the interpolation first.

  NOTE: The implementation is a temporary, ad-hoc heuristic that is needed solely to
        facilitate testing of the experimental "true smooth scrolling" feature. */
  fun isInterpolationEligibleFor(scrollbar: JScrollBar): Boolean {
    val window = scrollbar.topLevelAncestor as Window?
    if (window is JDialog && window.title == "Commit Changes") {
      return false
    }

    if (window !is RootPaneContainer) {
      return true
    }

    val components = window.contentPane.components
    if (components.size == 1 && components[0].javaClass.name.contains("DiffWindow")) {
      return false
    }

    if (components.size == 2) {
      val firstComponent = components[0]
      if (firstComponent is Container) {
        val subComponents = firstComponent.components
        if (subComponents.size == 1) {
          val name = subComponents[0].javaClass.name
          if (name.contains("DiffWindow") || name.contains("MergeWindow")) {
            return false
          }
        }
      }
    }

    return true
  }
}
