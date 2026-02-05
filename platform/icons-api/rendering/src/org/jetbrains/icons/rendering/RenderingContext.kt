// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import org.jetbrains.icons.ExperimentalIconsApi

/**
 * Rendering context affects the behavior of the actual renderers and can be used to, for example, pass update callbacks.
 */
@ExperimentalIconsApi
class RenderingContext(
  /**
   * Update flow notifies the Icon renderer user about the need to force-rerender the Icon if necessary. (for example on animation frame)
   */
  val updateFlow: MutableIconUpdateFlow,
  /**
   * Default image modifiers that will be applied to the Icon renderer, can be used to set default color filters etc.
   * This is mainly used when Icon is layered into another Icon, to pass the color filters etc., but can be also used
   * to pass the filters from the component Icon should be rendered in.
   */
  val defaultImageModifiers: ImageModifiers? = null
) {
  fun copy(updateFlow: MutableIconUpdateFlow? = null, defaultImageModifiers: ImageModifiers? = null): RenderingContext = RenderingContext(
    updateFlow ?: this.updateFlow,
    defaultImageModifiers ?: this.defaultImageModifiers
  )

  companion object {
    val Empty: RenderingContext = RenderingContext(IconRendererManager.createUpdateFlow(null) { })
  }
}