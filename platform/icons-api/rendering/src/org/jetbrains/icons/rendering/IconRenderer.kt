// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.rendering

import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.Icon
import org.jetbrains.icons.InternalIconsApi

@ExperimentalIconsApi
interface IconRenderer {
  val icon: Icon
  @InternalIconsApi
  fun render(api: PaintingApi)
  @InternalIconsApi
  fun calculateExpectedDimensions(scaling: ScalingContext): Dimensions
}