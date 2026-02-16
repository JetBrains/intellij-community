// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.impl.DefaultDeferredIcon
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.IconRenderer
import org.jetbrains.icons.rendering.LoadingStrategy
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.rendering.createRenderer

// TODO Implement actual resolving & data transfer
internal class DefaultDeferredIconRenderer(
  override val icon: DefaultDeferredIcon,
  val renderingContext: RenderingContext,
  loadingStrategy: LoadingStrategy
): IconRenderer {
  private var currentIcon = icon.currentIcon
  private var renderer = currentIcon?.createRenderer(renderingContext, loadingStrategy)

  @ApiStatus.Internal
  override fun render(api: PaintingApi) {
    renderer?.render(api)
  }

  @ApiStatus.Internal
  override fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions {
    return renderer?.calculateExpectedDimensions(scaling) ?: Dimensions(0, 0)
  }
}
