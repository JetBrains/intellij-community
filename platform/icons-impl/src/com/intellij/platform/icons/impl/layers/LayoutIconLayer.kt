// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.layers

import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import kotlinx.serialization.Serializable

@Serializable
class LayoutIconLayer(
    val nestedLayers: List<IconLayer>,
    val direction: LayoutDirection,
    override val modifier: IconModifier,
) : IconLayer {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LayoutIconLayer

        if (nestedLayers != other.nestedLayers) return false
        if (direction != other.direction) return false
        if (modifier != other.modifier) return false

        return true
    }

    override fun hashCode(): Int {
        var result = nestedLayers.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + modifier.hashCode()
        return result
    }

    override fun toString(): String =
        "LayoutIconLayer(nestedLayers=$nestedLayers, direction=$direction, modifier=$modifier)"

    enum class LayoutDirection {
        Row,
        Column,
        Box,
    }
}
