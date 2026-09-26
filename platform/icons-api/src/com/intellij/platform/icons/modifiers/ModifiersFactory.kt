// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.modifiers

import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.patchers.SvgPatcher
import com.intellij.platform.icons.scale.IconScale
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ModifiersFactory {
    fun combine(a: IconModifier, b: IconModifier): IconModifier

    fun align(align: IconAlign): IconModifier

    fun alpha(alpha: Float): IconModifier

    fun colorFilter(colorFilter: ColorFilter): IconModifier

    fun cutoutMargin(size: IconUnit): IconModifier

    fun margin(left: IconUnit, top: IconUnit, right: IconUnit, bottom: IconUnit): IconModifier

    fun scale(scale: IconScale): IconModifier

    fun stroke(color: Color): IconModifier

    fun patchSvg(svgPatcher: SvgPatcher): IconModifier
}
