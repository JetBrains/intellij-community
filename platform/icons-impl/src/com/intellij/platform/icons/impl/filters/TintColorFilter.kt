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

        if (color != other.color) return false
        if (blendMode != other.blendMode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + blendMode.hashCode()
        return result
    }

    override fun toString(): String = "TintColorFilter(color=$color, blendMode=$blendMode)"
}
