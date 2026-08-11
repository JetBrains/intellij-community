// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.design

import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.design.Pixel
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
class DefaultPixel(override val value: Int) : Pixel {
    override fun plus(other: Pixel): Pixel = DefaultPixel(value + other.value)

    override fun times(other: Int): IconUnit = DefaultPixel(value * other)

    override fun times(other: Double): IconUnit = DefaultPixel((value * other).roundToInt())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultPixel

        return value == other.value
    }

    override fun hashCode(): Int = value

    override fun toString(): String = "PixelIconUnit(value=$value)"
}
