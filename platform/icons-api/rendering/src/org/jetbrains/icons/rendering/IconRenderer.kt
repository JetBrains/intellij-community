// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.Icon

@ApiStatus.Experimental
interface IconRenderer {
  val icon: Icon
  @ApiStatus.Internal
  fun render(api: PaintingApi)
  @ApiStatus.Internal
  fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions
}