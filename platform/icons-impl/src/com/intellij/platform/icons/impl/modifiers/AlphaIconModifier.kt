// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import kotlinx.serialization.Serializable

@Serializable
class AlphaIconModifier(val alpha: Float) : ApplyableIconModifier {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlphaIconModifier

        return alpha == other.alpha
    }

    override fun hashCode(): Int = alpha.hashCode()

    override fun toString(): String = "AlphaIconModifier(alpha=$alpha)"

    override fun applyTo(layout: LayerLayout): LayerLayout = layout.copy(alpha = alpha)
}
