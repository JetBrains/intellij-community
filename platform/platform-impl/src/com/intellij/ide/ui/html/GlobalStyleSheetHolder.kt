// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.ide.ui.html

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.platform.diagnostic.telemetry.impl.span
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
private val globalStyleSheet = StyleSheet()

private var currentLafStyleSheet: StyleSheet? = null

// used by Material Theme - cannot be internal
@Suppress("unused")
@Internal
object GlobalStyleSheetHolder {
  fun getGlobalStyleSheet(): StyleSheet = createGlobalStyleSheet()
}

/**
 * Returns a global style sheet dynamically updated when LAF changes
 */
@Internal
fun createGlobalStyleSheet(): StyleSheet {
  // return a linked sheet to avoid mutation of a global variable
  val result = StyleSheet()
  result.addStyleSheet(globalStyleSheet)
  return result
}

/**
 * Populate global stylesheet with LAF-based overrides
 */
internal suspend fun updateGlobalStyleSheet() {
  val newStyle = StyleSheet()
  newStyle.addRule(getCssForCurrentLaf())
  newStyle.addRule(getCssForCurrentEditorScheme())

  withContext(RawSwingDispatcher) {
    currentLafStyleSheet?.let {
      globalStyleSheet.removeStyleSheet(it)
    }

    currentLafStyleSheet = newStyle
    globalStyleSheet.addStyleSheet(newStyle)
  }
}

@Internal
suspend fun initGlobalStyleSheet() {
  coroutineScope {
    launch(CoroutineName("EditorColorsManager preloading")) {
      serviceAsync<EditorColorsManager>()
    }

    withContext(RawSwingDispatcher) {
      span("global styleSheet updating") {
        val kit = HTMLEditorKit()
        val defaultSheet = kit.styleSheet
        globalStyleSheet.addStyleSheet(defaultSheet)

        // ... set a new default sheet
        kit.styleSheet = createGlobalStyleSheet()
      }
    }

    service<GlobalStyleSheetUpdateService>().init()
  }
}

@Service(Service.Level.APP)
@OptIn(FlowPreview::class)
private class GlobalStyleSheetUpdateService(private val coroutineScope: CoroutineScope) {
  private val updateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  suspend fun init() {
    doUpdateGlobalStyleSheet()

    coroutineScope.launch {
      updateRequests.debounce(50.milliseconds).collectLatest {
        doUpdateGlobalStyleSheet()
      }
    }
  }

  private suspend fun doUpdateGlobalStyleSheet() {
    span("global styleSheet updating") {
      updateGlobalStyleSheet()
    }
  }

  fun requestUpdate() {
    updateRequests.tryEmit(Unit)
  }
}

private class GlobalStyleSheetUpdateListener : EditorColorsListener, LafManagerListener {
  override fun lookAndFeelChanged(source: LafManager) {
    serviceIfCreated<GlobalStyleSheetUpdateService>()?.requestUpdate()
  }

  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    serviceIfCreated<GlobalStyleSheetUpdateService>()?.requestUpdate()
  }
}
