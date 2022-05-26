// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.html

import com.intellij.diagnostic.runActivity
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

/**
 * Holds a reference to global CSS style sheet that should be used by [HTMLEditorKit] to properly render everything
 * with respect to Look-and-Feel
 *
 * Based on a default swing stylesheet at javax/swing/text/html/default.css
 */
@Internal
@Service(Service.Level.APP)
class GlobalStyleSheetHolder {
  private val globalStyleSheet = StyleSheet()
  private var swingStyleSheetHandled = false
  private var currentLafStyleSheet: StyleSheet? = null

  companion object {
    fun getInstance(): GlobalStyleSheetHolder = service()
  }

  /**
   * Returns a global style sheet that is dynamically updated when LAF changes
   */
  fun getGlobalStyleSheet(): StyleSheet {
    val result = StyleSheet()
    // return a linked sheet to avoid mutation of a global variable
    result.addStyleSheet(globalStyleSheet)
    return result
  }

  /**
   * Populate global stylesheet with LAF-based overrides
   */
  internal fun updateGlobalStyleSheet() {
    runActivity("global styleSheet updating") {
      if (!swingStyleSheetHandled) {
        // get the default JRE CSS and ...
        val kit = HTMLEditorKit()
        val defaultSheet = kit.styleSheet
        globalStyleSheet.addStyleSheet(defaultSheet)

        // ... set a new default sheet
        kit.styleSheet = getGlobalStyleSheet()
        swingStyleSheetHandled = true
      }

      val currentSheet = currentLafStyleSheet
      if (currentSheet != null) {
        globalStyleSheet.removeStyleSheet(currentSheet)
      }

      val newStyle = StyleSheet()
      val lafCssProvider = service<LafCssProvider>()
      newStyle.addRule(lafCssProvider.getCssForCurrentLaf())
      newStyle.addRule(lafCssProvider.getCssForCurrentEditorScheme())
      currentLafStyleSheet = newStyle
      globalStyleSheet.addStyleSheet(newStyle)
    }
  }

  internal class UpdateListener : EditorColorsListener, LafManagerListener {
    override fun lookAndFeelChanged(source: LafManager) {
      getInstance().updateGlobalStyleSheet()
    }

    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
      getInstance().updateGlobalStyleSheet()
    }
  }
}