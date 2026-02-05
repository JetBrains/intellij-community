// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.design

import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.impl.modifiers.asFractionalPixels
import com.intellij.platform.icons.impl.rendering.DefaultScalingContext
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.FillAreaScale
import com.intellij.platform.icons.scale.FitAreaScale
import kotlinx.serialization.Serializable

@Serializable
class DefaultFactorScale(override val factor: Double) : FactorScale {
    override fun toFactor(density: Float, originalWidth: Int, originalHeight: Int, contextScale: Float): Float =
        factor.toFloat()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultFactorScale

        return factor == other.factor
    }

    override fun hashCode(): Int = factor.hashCode()
}

@Serializable
class DefaultFitAreaScale(override val width: IconUnit, override val height: IconUnit, override val relative: Boolean) :
    FitAreaScale {
    override fun toFactor(density: Float, originalWidth: Int, originalHeight: Int, contextScale: Float): Float {
        val scaling = DefaultScalingContext(density, if (relative) contextScale else 1f)
        val realWidth = width.asFractionalPixels(scaling)
        val realHeight = height.asFractionalPixels(scaling)
        val widthFactor = realWidth / originalWidth
        val heightFactor = realHeight / originalHeight
        return widthFactor.coerceAtMost(heightFactor)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultFitAreaScale

        if (relative != other.relative) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = relative.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        return result
    }

    override fun toString(): String = "DefaultFitAreaScale(width=$width, height=$height, relative=$relative)"
}

@Serializable
class DefaultFillAreaScale(
    override val width: IconUnit,
    override val height: IconUnit,
    override val relative: Boolean,
) : FillAreaScale {
    override fun toFactor(density: Float, originalWidth: Int, originalHeight: Int, contextScale: Float): Float {
        val scaling = DefaultScalingContext(density, if (relative) contextScale else 1f)
        val realWidth = width.asFractionalPixels(scaling)
        val realHeight = height.asFractionalPixels(scaling)
        val widthFactor = originalWidth / realWidth
        val heightFactor = originalHeight / realHeight
        return widthFactor.coerceAtLeast(heightFactor)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultFillAreaScale

        if (relative != other.relative) return false
        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = relative.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        return result
    }

    override fun toString(): String = "DefaultFillAreaScale(width=$width, height=$height, relative=$relative)"
}
