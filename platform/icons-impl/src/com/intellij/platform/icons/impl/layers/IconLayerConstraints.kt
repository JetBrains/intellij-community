// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.layers

import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.design.IconMargin
import com.intellij.platform.icons.design.IconUnit
import kotlinx.serialization.Serializable

@Serializable
class IconLayerConstraints(
    val align: IconAlign,
    val width: IconUnit,
    val height: IconUnit,
    val margin: IconMargin,
    val alpha: Float = 1.0f,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IconLayerConstraints

        if (align != other.align) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (margin != other.margin) return false
        if (alpha != other.alpha) return false

        return true
    }

    override fun hashCode(): Int {
        var result = align.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + margin.hashCode()
        result = 31 * result + alpha.hashCode()
        return result
    }

    override fun toString(): String =
        "IconLayerConstraints(align=$align, width=$width, height=$height, margin=$margin, opacity=$alpha)"
}
