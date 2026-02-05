// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.ModifiersFactory
import com.intellij.platform.icons.patchers.SvgPatcher
import com.intellij.platform.icons.scale.IconScale

object DefaultModifiersFactory : ModifiersFactory {
    override fun combine(a: IconModifier, b: IconModifier): IconModifier {
        val aCasted = a as? ApplyableIconModifier ?: RootIconModifier
        val bCasted = b as? ApplyableIconModifier ?: RootIconModifier
        return CombinedIconModifier(aCasted, bCasted)
    }

    override fun align(align: IconAlign): IconModifier = AlignIconModifier(align)

    override fun alpha(alpha: Float): IconModifier = AlphaIconModifier(alpha)

    override fun colorFilter(colorFilter: ColorFilter): IconModifier = ColorFilterModifier(colorFilter)

    override fun cutoutMargin(size: IconUnit): IconModifier = CutoutMarginModifier(size)

    override fun margin(left: IconUnit, top: IconUnit, right: IconUnit, bottom: IconUnit): IconModifier =
        MarginIconModifier(left, top, right, bottom)

    override fun stroke(color: Color): IconModifier = StrokeModifier(color)

    override fun scale(scale: IconScale): IconModifier = ScaleModifier(scale)

    override fun patchSvg(svgPatcher: SvgPatcher): IconModifier =
        SvgPatcherModifier(svgPatcher as? DefaultSvgPatcher ?: error("Unsupported svgPatcher: $svgPatcher"))
}
