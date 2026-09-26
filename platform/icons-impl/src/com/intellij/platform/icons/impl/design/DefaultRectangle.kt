// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.design

import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.design.Rectangle
import kotlinx.serialization.Serializable

@Serializable
class DefaultRectangle(override val width: IconUnit, override val height: IconUnit) : Rectangle {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultRectangle

        if (width != other.width) return false
        if (height != other.height) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width.hashCode()
        result = 31 * result + height.hashCode()
        return result
    }

    override fun toString(): String = "DefaultRectangle(width=$width, height=$height)"
}
