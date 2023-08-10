// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.html

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
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
  private suspend fun updateGlobalStyleSheet() {
    val newStyle = StyleSheet()
    newStyle.addRule(LafCssProvider.getCssForCurrentLaf())
    newStyle.addRule(LafCssProvider.getCssForCurrentEditorScheme())

    withContext(RawSwingDispatcher + ModalityState.any().asContextElement()) {
      currentLafStyleSheet?.let {
        globalStyleSheet.removeStyleSheet(it)
      }

      currentLafStyleSheet = newStyle
      globalStyleSheet.addStyleSheet(newStyle)
    }
  }

  @Service(Service.Level.APP)
  @OptIn(FlowPreview::class)
  private class UpdateService(coroutineScope: CoroutineScope) {
    private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
      coroutineScope.launch {
        updateRequests
          .debounce(5.milliseconds)
          .collectLatest {
            withContext(CoroutineName("global styleSheet updating")) {
              updateGlobalStyleSheet()
            }
          }
      }
    }

    fun requestUpdate() {
      check(updateRequests.tryEmit(Unit))
    }
  }

  internal class UpdateListener : EditorColorsListener, LafManagerListener {
    override fun lookAndFeelChanged(source: LafManager) {
      service<UpdateService>().requestUpdate()
    }

    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
      service<UpdateService>().requestUpdate()
    }
  }
}
