// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.rendering.ImageModifiers

/**
 * The modifiers of an image layer, resolved from its own modifiers, the modifiers it inherits and the theme.
 *
 * @param isDark the dark state to use if [darkOverride] is `null`. For the modifiers of a rendering context, it is the
 *   fallback for a theme that does not supply a dark state.
 */
open class DefaultImageModifiers(
    override val colorFilter: ColorFilter? = null,
    override val svgPatcher: DefaultSvgPatcher? = null,
    isDark: Boolean = false,
    val stroke: Color? = null,
    /** How image layers use `@WxH` size variants; inherited by nested layers unless they set their own. */
    val sizeVariantPolicy: SizeVariantPolicy = SizeVariantPolicy.Exact,
    /**
     * The explicit dark state: `true` for the dark artwork, `false` for the light artwork. `null` follows the theme.
     * Nested layers inherit it unless they set their own.
     */
    val darkOverride: Boolean? = null,
) : ImageModifiers {
    /** Keeps the constructor signature from before [sizeVariantPolicy] existed (binary compatibility). */
    constructor(colorFilter: ColorFilter?, svgPatcher: DefaultSvgPatcher?, isDark: Boolean, stroke: Color?) :
        this(colorFilter, svgPatcher, isDark, stroke, SizeVariantPolicy.Exact, null)

    /** Keeps the constructor signature from before [darkOverride] existed (binary compatibility). */
    constructor(
        colorFilter: ColorFilter?,
        svgPatcher: DefaultSvgPatcher?,
        isDark: Boolean,
        stroke: Color?,
        sizeVariantPolicy: SizeVariantPolicy,
    ) : this(colorFilter, svgPatcher, isDark, stroke, sizeVariantPolicy, null)

    /** The effective dark state: [darkOverride] if it is set. */
    val isDark: Boolean = darkOverride ?: isDark

    override fun toString(): String =
        "DefaultImageModifiers(colorFilter=$colorFilter, svgPatcher=$svgPatcher, isDark=$isDark, " +
        "darkOverride=$darkOverride, stroke=$stroke, sizeVariantPolicy=$sizeVariantPolicy)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultImageModifiers

        if (isDark != other.isDark) return false
        if (darkOverride != other.darkOverride) return false
        if (colorFilter != other.colorFilter) return false
        if (svgPatcher != other.svgPatcher) return false
        if (stroke != other.stroke) return false
        if (sizeVariantPolicy != other.sizeVariantPolicy) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isDark.hashCode()
        result = 31 * result + (darkOverride?.hashCode() ?: 0)
        result = 31 * result + (colorFilter?.hashCode() ?: 0)
        result = 31 * result + (svgPatcher?.hashCode() ?: 0)
        result = 31 * result + (stroke?.hashCode() ?: 0)
        result = 31 * result + sizeVariantPolicy.hashCode()
        return result
    }
}
