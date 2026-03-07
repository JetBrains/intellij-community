// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.filters

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.design.BlendMode
import org.jetbrains.icons.design.Color

@ApiStatus.Experimental
@Serializable
class TintColorFilter(
  val color: Color,
  val blendMode: BlendMode = BlendMode.SrcIn
): ColorFilter {
}
