// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The dark state of a Swing icon as a whole, for example after `IconLoader.getDarkIcon`: [isDark] `true` uses the dark
 * artwork, `false` the light artwork. It is not a modifier of the icon designer. A Swing icon adds it when it converts to
 * a descriptor, and it reads and removes only this modifier. So a dark state that the descriptor sets with
 * [ForceDarkVariantModifier] stays. A [ForceDarkVariantModifier] of the same layer wins over it, and nested layers
 * inherit it. It does not change the layout.
 */
@ApiStatus.Internal
@Serializable
class DarkStateModifier(val isDark: Boolean) : ApplyableIconModifier {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DarkStateModifier

        return isDark == other.isDark
    }

    override fun hashCode(): Int = isDark.hashCode()

    override fun toString(): String = "DarkStateModifier(isDark=$isDark)"

    override fun applyTo(layout: LayerLayout): LayerLayout = layout
}
