// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import kotlinx.serialization.Serializable

@Serializable
class StrokeModifier(val color: Color) : ApplyableIconModifier {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StrokeModifier

        return color == other.color
    }

    override fun hashCode(): Int = color.hashCode()

    override fun toString(): String = "StrokeIconModifier(color=$color)"

    override fun applyTo(layout: LayerLayout): LayerLayout = layout.copy(stroke = color)
}
