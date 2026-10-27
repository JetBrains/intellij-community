// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.filters

import com.intellij.platform.icons.design.BlendMode
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.filters.ColorFilterFactory

object DefaultColorFilterFactory : ColorFilterFactory {
    override fun tintColor(color: Color, blendMode: BlendMode): ColorFilter = TintColorFilter(color, blendMode)

    override fun gray(brightness: Int, contrast: Int, alpha: Int): ColorFilter =
        GrayColorFilter(brightness.coerceIn(-100, 100), contrast.coerceIn(-100, 100), alpha.coerceIn(0, 100))
}
