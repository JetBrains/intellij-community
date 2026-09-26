// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij.rendering

import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CoroutineScope
import com.intellij.platform.icons.impl.intellij.rendering.images.IntelliJImageResourceProvider
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.impl.rendering.CoroutineBasedMutableIconUpdateFlow
import com.intellij.platform.icons.impl.rendering.DefaultIconRendererManager
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.layers.IconLayerRenderer
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext

@Suppress("UNCHECKED_CAST")
class IntelliJIconRendererManager: DefaultIconRendererManager() {
  private val imageProvider = IntelliJImageResourceProvider()
  private val themeContext = IntelliJThemeContext()

  override fun createRenderer(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer {
    if (layer is SwingIconLayer) {
      return SwingIconLayerRenderer(layer, renderingContext as DefaultRenderingContext)
    } else {
      return super.createRenderer(layer, renderingContext)
    }
  }

  override fun createUpdateFlow(scope: CoroutineScope?, onUpdate: (suspend (Int) -> Unit)?): MutableIconUpdateFlow {
    if (scope != null) {
      return CoroutineBasedMutableIconUpdateFlow(scope, onUpdate)
    } else {
      return IntelliJMutableIconUpdateFlowImpl(onUpdate)
    }
  }

  override fun createRenderingContext(
    updateFlow: MutableIconUpdateFlow,
    defaultImageModifiers: ImageModifiers?,
  ): RenderingContext {
    val knownModifiers = defaultImageModifiers as? DefaultImageModifiers
    return DefaultRenderingContext(
      updateFlow,
      DefaultImageModifiers(
        knownModifiers?.colorFilter,
        knownModifiers?.svgPatcher,
        StartupUiUtil.isDarkTheme,
        knownModifiers?.stroke
      ),
      themeContext,
      imageProvider
    )
  }
}