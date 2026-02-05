// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import com.intellij.platform.icons.impl.rendering.layers.compoundSize
import kotlinx.serialization.Serializable

@Serializable
class MarginIconModifier(val left: IconUnit, val top: IconUnit, val right: IconUnit, val bottom: IconUnit) :
    ApplyableIconModifier {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MarginIconModifier

        if (left != other.left) return false
        if (top != other.top) return false
        if (right != other.right) return false
        if (bottom != other.bottom) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + top.hashCode()
        result = 31 * result + right.hashCode()
        result = 31 * result + bottom.hashCode()
        return result
    }

    override fun toString(): String = "MarginIconModifier(left=$left, top=$top, right=$right, bottom=$bottom)"

    override fun applyTo(layout: LayerLayout): LayerLayout =
        layout.copy(
            leftMargin = left.compoundSize(),
            topMargin = top.compoundSize(),
            rightMargin = right.compoundSize(),
            bottomMargin = bottom.compoundSize(),
        )
}
