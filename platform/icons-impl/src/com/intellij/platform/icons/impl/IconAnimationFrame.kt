// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl

import com.intellij.platform.icons.layers.IconLayer
import kotlinx.serialization.Serializable

@Serializable
class IconAnimationFrame(val layers: List<IconLayer>, val duration: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IconAnimationFrame

        if (duration != other.duration) return false
        if (layers != other.layers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = duration.hashCode()
        result = 31 * result + layers.hashCode()
        return result
    }

    override fun toString(): String = "IconAnimationFrame(duration=$duration, layers=$layers)"
}
