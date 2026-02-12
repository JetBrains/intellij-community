// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.design

import kotlinx.serialization.Serializable
import org.jetbrains.icons.ExperimentalIconsApi

@ExperimentalIconsApi
@Serializable
enum class BlendMode {
  SrcIn,
  Hue,
  Saturation,
  Luminosity,
  Color,
  Multiply
}