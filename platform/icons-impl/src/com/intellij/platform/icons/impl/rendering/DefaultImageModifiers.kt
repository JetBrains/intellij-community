// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.rendering.ImageModifiers

open class DefaultImageModifiers(
    override val colorFilter: ColorFilter? = null,
    override val svgPatcher: DefaultSvgPatcher? = null,
    val isDark: Boolean = false,
    val stroke: Color? = null,
) : ImageModifiers {
    override fun toString(): String =
        "DefaultImageModifiers(colorFilter=$colorFilter, svgPatcher=$svgPatcher, isDark=$isDark, stroke=$stroke)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultImageModifiers

        if (isDark != other.isDark) return false
        if (colorFilter != other.colorFilter) return false
        if (svgPatcher != other.svgPatcher) return false
        if (stroke != other.stroke) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDark.hashCode()
        result = 31 * result + (colorFilter?.hashCode() ?: 0)
        result = 31 * result + (svgPatcher?.hashCode() ?: 0)
        result = 31 * result + (stroke?.hashCode() ?: 0)
        return result
    }
}
