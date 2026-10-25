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
import com.intellij.platform.icons.modifiers.SizeVariantPolicy

@Suppress("UNCHECKED_CAST")
class IntelliJIconRendererManager: DefaultIconRendererManager() {
  private val imageProvider = IntelliJImageResourceProvider()
  private val themeContext = intelliJThemeContext

  override fun createRenderer(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer {
    if (layer is SwingIconLayer) {
      return SwingIconLayerRenderer(layer, renderingContext as DefaultRenderingContext)
    } else {
      return super.createRenderer(layer, renderingContext)
    }
  }

  override fun createUpdateFlow(scope: CoroutineScope?, onUpdate: (suspend (Int) -> Unit)?): MutableIconUpdateFlow {
    // Swing icons paint again on `updateUI` after a LaF change. A Compose icon caches its drawing, so its update flow
    // also emits after a LaF change.
    if (scope != null) {
      return ThemeAwareIconUpdateFlow(CoroutineBasedMutableIconUpdateFlow(scope, onUpdate))
    } else {
      return ThemeAwareIconUpdateFlow(IntelliJMutableIconUpdateFlowImpl(onUpdate))
    }
  }

  override fun createRenderingContext(
    updateFlow: MutableIconUpdateFlow,
    defaultImageModifiers: ImageModifiers?,
  ): RenderingContext {
    val knownModifiers = defaultImageModifiers as? DefaultImageModifiers
    // The caller's modifiers are kept as they are. The image layers take the dark state of the theme on each renderer
    // build unless the caller or a layer sets `darkOverride`, so `isDark` here is only the fallback.
    val modifiers = DefaultImageModifiers(
      colorFilter = knownModifiers?.colorFilter,
      svgPatcher = knownModifiers?.svgPatcher,
      isDark = knownModifiers?.isDark ?: StartupUiUtil.isDarkTheme,
      stroke = knownModifiers?.stroke,
      sizeVariantPolicy = knownModifiers?.sizeVariantPolicy ?: SizeVariantPolicy.Exact,
      darkOverride = knownModifiers?.darkOverride,
    )
    return DefaultRenderingContext(updateFlow, modifiers, themeContext, imageProvider)
  }
}