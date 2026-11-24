// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import kotlinx.serialization.Serializable

/**
 * Forces the artwork of the image layers it applies to: [isDark] `true` uses the dark artwork, `false` the light artwork.
 * `generateImageModifiers` resolves it into `DefaultImageModifiers.forcedDark`, and nested layers inherit it. It does not
 * change the layout.
 */
@Serializable
class ForceDarkVariantModifier(val isDark: Boolean) : ApplyableIconModifier {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ForceDarkVariantModifier

        return isDark == other.isDark
    }

    override fun hashCode(): Int = isDark.hashCode()

    override fun toString(): String = "ForceDarkVariantModifier(isDark=$isDark)"

    override fun applyTo(layout: LayerLayout): LayerLayout = layout
}
