// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.layers

import org.jetbrains.icons.rendering.Dimensions
import org.jetbrains.icons.rendering.PaintingApi
import org.jetbrains.icons.rendering.ScalingContext

interface IconLayerRenderer {
  fun render(api: PaintingApi)
  fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions
}

