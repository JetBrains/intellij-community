// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import com.intellij.platform.icons.scale.IconScale
import kotlinx.serialization.Serializable

@Serializable
class ScaleModifier(val scale: IconScale) : ApplyableIconModifier {
    override fun applyTo(layout: LayerLayout): LayerLayout = layout.copy(scale = scale)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ScaleModifier

        return scale == other.scale
    }

    override fun hashCode(): Int = scale.hashCode()

    override fun toString(): String = "ScaleModifier(scale=$scale)"
}
