// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.icons.DynamicIcon
import org.jetbrains.icons.Icon
import org.jetbrains.icons.InternalIconsApi
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.IconRenderer
import org.jetbrains.icons.rendering.LoadingStrategy
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.rendering.createRenderer
import java.lang.ref.WeakReference

internal class DefaultDynamicIconRenderer(
  override val icon: DynamicIcon,
  val renderingContext: RenderingContext,
  loadingStrategy: LoadingStrategy
): IconRenderer {
  private var currentIcon = icon.getCurrentIcon()
  private var renderer = currentIcon.createRenderer(renderingContext, loadingStrategy)

  init {
    val ref = WeakReference(this)
    renderingContext.updateFlow.collectDynamic(icon.getFlow()) {
      ref.get()?.swapIcons(it)
    }
  }

  private fun swapIcons(newIcon: Icon) {
    if (currentIcon === newIcon) return
    currentIcon = newIcon
    renderer = newIcon.createRenderer(renderingContext, LoadingStrategy.RenderPlaceholder(renderer))
    renderingContext.updateFlow.triggerUpdate()
  }

  @InternalIconsApi
  override fun render(api: PaintingApi) {
    renderer.render(api)
  }

  @InternalIconsApi
  override fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions {
    return renderer.calculateExpectedDimensions(scaling)
  }
}
