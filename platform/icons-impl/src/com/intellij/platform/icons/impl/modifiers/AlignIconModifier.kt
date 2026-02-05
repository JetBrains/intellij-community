// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.design.IconAlign
import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import kotlinx.serialization.Serializable

@Serializable
class AlignIconModifier(val align: IconAlign) : ApplyableIconModifier {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlignIconModifier

        return align == other.align
    }

    override fun hashCode(): Int = align.hashCode()

    override fun toString(): String = "AlignIconModifier(align=$align)"

    override fun applyTo(layout: LayerLayout): LayerLayout = layout.copy(align = align)
}
