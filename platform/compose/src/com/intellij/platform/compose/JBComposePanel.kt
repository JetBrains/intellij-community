// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.platform.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.jewel.bridge.actionSystem.ComponentDataProviderBridge
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.skiko.ExperimentalSkikoApi
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.SkiaLayerAnalytics
import javax.swing.JComponent

@Suppress("FunctionName")
@OptIn(ExperimentalComposeUiApi::class, ExperimentalJewelApi::class)
@Experimental
fun JBComposePanel(
  content: @Composable () -> Unit
): JComponent {
  return ComposePanel(ComposeAnalytics()).apply {
    setContent {
      SwingBridgeTheme {
        CompositionLocalProvider(LocalComponent provides this@apply) {
          ComponentDataProviderBridge(this@apply) {
            content()
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalSkikoApi::class)
private class ComposeAnalytics : SkiaLayerAnalytics {
  override fun renderer(skikoVersion: String, os: OS, api: GraphicsApi): SkiaLayerAnalytics.RendererAnalytics {
    return object : SkiaLayerAnalytics.RendererAnalytics {
      override fun init() {
        LOG.info("Compose panel is initialized with graphicsApi: $api")
      }
    }
  }

  companion object {
    private val LOG = logger<ComposeAnalytics>()
  }
}