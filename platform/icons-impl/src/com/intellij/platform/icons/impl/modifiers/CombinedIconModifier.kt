// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.modifiers

import com.intellij.platform.icons.impl.rendering.layers.LayerLayout
import kotlinx.serialization.Serializable

@Serializable
class CombinedIconModifier(val root: ApplyableIconModifier, val other: ApplyableIconModifier) : ApplyableIconModifier {
    override fun applyTo(layout: LayerLayout): LayerLayout = other.applyTo(root.applyTo(layout))

    override fun toString(): String = "CombinedIconModifier(root=$root, other=$other)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CombinedIconModifier

        if (root != other.root) return false
        if (this@CombinedIconModifier.other != other.other) return false

        return true
    }

    override fun hashCode(): Int {
        var result = root.hashCode()
        result = 31 * result + other.hashCode()
        return result
    }
}
