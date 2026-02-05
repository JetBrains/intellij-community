// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.modifiers

import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.BlendMode
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.filters.tintColorFilter

fun IconModifier.colorFilter(colorFilter: ColorFilter): IconModifier =
    this then IconManager.modifiers().colorFilter(colorFilter)

fun IconModifier.tintColor(color: Color, blendMode: BlendMode): IconModifier =
    colorFilter(tintColorFilter(color, blendMode))
