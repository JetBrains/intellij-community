// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.filters

import com.intellij.platform.icons.design.BlendMode
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.filters.ColorFilter
import kotlinx.serialization.Serializable

@Serializable
class TintColorFilter(val color: Color, val blendMode: BlendMode = BlendMode.SrcIn) : ColorFilter {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TintColorFilter

        return color == other.color && blendMode == other.blendMode
    }

    override fun hashCode(): Int = 31 * color.hashCode() + blendMode.hashCode()

    override fun toString(): String = "TintColorFilter(color=$color, blendMode=$blendMode)"
}
