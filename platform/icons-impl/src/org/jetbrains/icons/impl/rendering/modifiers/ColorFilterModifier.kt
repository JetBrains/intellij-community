// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.rendering.modifiers

import org.jetbrains.icons.modifiers.ColorFilterModifier
import org.jetbrains.icons.impl.rendering.layers.DefaultLayerLayout

internal fun applyColorFilterModifier(modifier: ColorFilterModifier, layout: DefaultLayerLayout, displayScale: Float): DefaultLayerLayout {
  return layout.copy(colorFilter = modifier.colorFilter)
}
