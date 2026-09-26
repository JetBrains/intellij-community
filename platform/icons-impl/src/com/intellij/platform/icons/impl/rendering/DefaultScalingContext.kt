// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.rendering

import com.intellij.platform.icons.rendering.ScalingContext

class DefaultScalingContext(override val displayDensity: Float, override val contextScale: Float) : ScalingContext {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultScalingContext

        if (displayDensity != other.displayDensity) return false
        if (contextScale != other.contextScale) return false

        return true
    }

    override fun hashCode(): Int {
        var result = displayDensity.hashCode()
        result = 31 * result + contextScale.hashCode()
        return result
    }

    override fun toString(): String =
        "DefaultScalingContext(displayDensity=$displayDensity, contextScale=$contextScale)"
}
