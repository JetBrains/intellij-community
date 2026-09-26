// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.design

import com.intellij.platform.icons.design.Circle
import com.intellij.platform.icons.design.IconUnit
import kotlinx.serialization.Serializable

@Serializable
class DefaultCircle(override val radius: IconUnit) : Circle {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultCircle

        return radius == other.radius
    }

    override fun hashCode(): Int = radius.hashCode()

    override fun toString(): String = "CircleIconUnit(radius=$radius)"
}
