// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.filters

import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.BlendMode
import com.intellij.platform.icons.design.Color

fun ColorFilter.Companion.tint(color: Color, blendMode: BlendMode = BlendMode.SrcIn): ColorFilter =
    IconManager.colorFilters().tintColor(color, blendMode)

@Deprecated("Use ColorFilter.tint(color, blendMode)", replaceWith = ReplaceWith("tintColorFilter(color, blendMode)"))
fun tintColorFilter(color: Color, blendMode: BlendMode = BlendMode.SrcIn): ColorFilter = ColorFilter.tint(color, blendMode)
