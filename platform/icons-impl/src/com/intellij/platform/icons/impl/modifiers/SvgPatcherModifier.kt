// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.impl.patchers.DefaultSvgPatcher
import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import kotlinx.serialization.Serializable

@Serializable
class SvgPatcherModifier(val svgPatcher: DefaultSvgPatcher) : ApplyableIconModifier {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SvgPatcherModifier

        return svgPatcher == other.svgPatcher
    }

    override fun hashCode(): Int = svgPatcher.hashCode()

    override fun toString(): String = "SvgPatcherModifier(svgPatcher=$svgPatcher)"

    override fun applyTo(layout: LayerLayout): LayerLayout {
        // This modifier doesn't affect layout
        return layout
    }
}
