// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.DeferredIcon
import org.jetbrains.icons.Icon
import org.jetbrains.icons.IconManager
import org.jetbrains.icons.impl.DefaultDeferredIcon
import org.jetbrains.icons.impl.DefaultIconManager
import org.jetbrains.icons.impl.DeferredIconEventHandler
import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.IconRenderer
import org.jetbrains.icons.rendering.LoadingStrategy
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.RenderingContext
import org.jetbrains.icons.rendering.ScalingContext
import org.jetbrains.icons.rendering.createRenderer

internal class DefaultDeferredIconRenderer(
  override val icon: DefaultDeferredIcon,
  val renderingContext: RenderingContext,
  val loadingStrategy: LoadingStrategy
): IconRenderer, DeferredIconEventHandler {
  private var isDone = false
  private var renderer = icon.placeholder?.createRenderer(renderingContext, loadingStrategy)

  override fun whenDone(deferredIcon: DeferredIcon, resolvedIcon: Icon) {
    val oldRenderer = renderer
    val strategy = if (oldRenderer != null) {
      LoadingStrategy.RenderPlaceholder(oldRenderer)
    } else loadingStrategy
    renderer = resolvedIcon.createRenderer(renderingContext, strategy)
    isDone = true
    renderingContext.updateFlow.triggerUpdate()
  }

  @ApiStatus.Internal
  override fun render(api: PaintingApi) {
    if (!isDone) {
      DefaultIconManager.getDefaultManagerInstance().scheduleEvaluation(icon)
    }
    renderer?.render(api)
  }

  @ApiStatus.Internal
  override fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions {
    return renderer?.calculateExpectedDimensions(scaling) ?: Dimensions(0, 0)
  }
}
