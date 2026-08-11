// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.design

import com.intellij.platform.icons.design.DisplayPoint
import com.intellij.platform.icons.design.IconUnit
import kotlinx.serialization.Serializable

@Serializable
class DefaultDisplayPoint(override val value: Double) : DisplayPoint {
    override fun plus(other: DisplayPoint): DisplayPoint = DefaultDisplayPoint(value + other.value)

    override fun times(other: Int): IconUnit = DefaultDisplayPoint(value * other)

    override fun times(other: Double): IconUnit = DefaultDisplayPoint(value * other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultDisplayPoint

        return value == other.value
    }

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "DisplayPointIconUnit(value=$value)"
}
