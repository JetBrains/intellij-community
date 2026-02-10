// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.intellij.rendering

import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.InternalIconsApi
import org.jetbrains.icons.impl.intellij.custom.CustomIconLayerRendererProvider
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.icons.rendering.MutableIconUpdateFlow
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.impl.rendering.CoroutineBasedMutableIconUpdateFlow
import org.jetbrains.icons.impl.rendering.DefaultIconRendererManager
import org.jetbrains.icons.layers.IconLayer
import org.jetbrains.icons.impl.rendering.DefaultImageModifiers
import org.jetbrains.icons.impl.rendering.layers.IconLayerRenderer

@Suppress("UNCHECKED_CAST")
@OptIn(InternalIconsApi::class, ExperimentalIconsApi::class)
class IntelliJIconRendererManager: DefaultIconRendererManager() {
  override fun createRenderer(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer {
    val defaultRenderer = createRendererOrNull(layer, renderingContext)
    if (defaultRenderer != null) return defaultRenderer

    return CustomIconLayerRendererProvider.createRendererFor(layer, renderingContext)
           ?: error("No renderer found for Icon Layer type: $layer\nMake sure that the corresponding renderer is properly registered.")
  }

  override fun createUpdateFlow(scope: CoroutineScope?, updateCallback: (Int) -> Unit): MutableIconUpdateFlow {
    if (scope != null) {
      return CoroutineBasedMutableIconUpdateFlow(scope, updateCallback)
    } else {
      return IntelliJMutableIconUpdateFlowImpl(updateCallback)
    }
  }

  override fun createRenderingContext(
    updateFlow: MutableIconUpdateFlow,
    defaultImageModifiers: ImageModifiers?,
  ): RenderingContext {
    val knownModifiers = defaultImageModifiers as? DefaultImageModifiers
    return RenderingContext(
      updateFlow,
      DefaultImageModifiers(
        defaultImageModifiers?.colorFilter,
        defaultImageModifiers?.svgPatcher,
        StartupUiUtil.isDarkTheme,
        knownModifiers?.stroke
      )
    )
  }
}