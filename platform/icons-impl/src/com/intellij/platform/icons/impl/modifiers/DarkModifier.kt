// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import kotlinx.serialization.Serializable

/**
 * Sets the dark state of the image layers it applies to: [dark] `true` uses the dark artwork, `false` the light artwork.
 * `generateImageModifiers` resolves it into `DefaultImageModifiers.darkOverride`, and nested layers inherit it. It does
 * not change the layout.
 */
@Serializable
class DarkModifier(val dark: Boolean) : ApplyableIconModifier {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DarkModifier

        return dark == other.dark
    }

    override fun hashCode(): Int = dark.hashCode()

    override fun toString(): String = "DarkModifier(dark=$dark)"

    override fun applyTo(layout: LayerLayout): LayerLayout = layout
}
