// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.html

import com.intellij.diagnostic.runActivity
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.impl.FontFamilyService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet
import kotlin.time.Duration.Companion.milliseconds

/**
 * Holds a reference to global CSS style sheet that should be used by [HTMLEditorKit] to properly render everything
 * with respect to Look-and-Feel
 *
 * Based on a default swing stylesheet at javax/swing/text/html/default.css
 */
@Internal
object GlobalStyleSheetHolder {
  private val globalStyleSheet = StyleSheet()
  private var currentLafStyleSheet: StyleSheet? = null

  /**
   * Returns a global style sheet that is dynamically updated when LAF changes
   */
  fun getGlobalStyleSheet(): StyleSheet {
    val result = StyleSheet()
    // return a linked sheet to avoid mutation of a global variable
    result.addStyleSheet(globalStyleSheet)
    return result
  }

  internal fun updateGlobalSwingStyleSheet() {
    // get the default JRE CSS and ...
    val kit = HTMLEditorKit()
    val defaultSheet = kit.styleSheet
    globalStyleSheet.addStyleSheet(defaultSheet)

    // ... set a new default sheet
    kit.styleSheet = getGlobalStyleSheet()
  }

  /**
   * Populate global stylesheet with LAF-based overrides
   */
  internal fun updateGlobalStyleSheet() {
    runActivity("global styleSheet updating") {
      val currentSheet = currentLafStyleSheet
      if (currentSheet != null) {
        globalStyleSheet.removeStyleSheet(currentSheet)
      }

      val newStyle = StyleSheet()
      newStyle.addRule(LafCssProvider.getCssForCurrentLaf())
      newStyle.addRule(LafCssProvider.getCssForCurrentEditorScheme())
      currentLafStyleSheet = newStyle
      globalStyleSheet.addStyleSheet(newStyle)
    }
  }

  @OptIn(FlowPreview::class)
  internal class UpdateListener : EditorColorsListener, LafManagerListener {
    private val updateRequests = MutableSharedFlow<Unit>(replay=1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
      ApplicationManager.getApplication().coroutineScope.launch {
        updateRequests
          .debounce(5.milliseconds)
          .collectLatest {
            val componentManager = ApplicationManager.getApplication() as ComponentManagerEx
            listOf(
              componentManager.getServiceAsync(EditorColorsManager::class.java),
              componentManager.getServiceAsync(FontFamilyService::class.java),
            ).awaitAll()
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
              updateGlobalStyleSheet()
            }
          }
      }
    }

    override fun lookAndFeelChanged(source: LafManager) {
      check(updateRequests.tryEmit(Unit))
    }

    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
      check(updateRequests.tryEmit(Unit))
    }
  }
}