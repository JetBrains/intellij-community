// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.layers

import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.Shape
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import kotlinx.serialization.Serializable

@Serializable
class ShapeIconLayer(val color: Color, val shape: Shape, override val modifier: IconModifier) : IconLayer {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ShapeIconLayer

        if (color != other.color) return false
        if (shape != other.shape) return false
        if (modifier != other.modifier) return false

        return true
    }

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + shape.hashCode()
        result = 31 * result + modifier.hashCode()
        return result
    }

    override fun toString(): String = "BadgeIconLayer(color=$color, badgeShape=$shape, modifier=$modifier)"
}
