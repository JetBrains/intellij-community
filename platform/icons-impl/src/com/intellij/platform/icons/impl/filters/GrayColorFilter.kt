// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.filters

import com.intellij.platform.icons.filters.ColorFilter
import kotlinx.serialization.Serializable

/**
 * See [com.intellij.platform.icons.filters.gray] for the meaning of the values.
 *
 * The constructor and the deserialization reject a value outside its range.
 */
@Serializable
class GrayColorFilter(val brightness: Int, val contrast: Int, val alpha: Int) : ColorFilter {
    init {
        require(brightness in -100..100) { "brightness must be in -100..100 but was $brightness" }
        require(contrast in -100..100) { "contrast must be in -100..100 but was $contrast" }
        require(alpha in 0..100) { "alpha must be in 0..100 but was $alpha" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GrayColorFilter

        if (brightness != other.brightness) return false
        if (contrast != other.contrast) return false
        if (alpha != other.alpha) return false

        return true
    }

    override fun hashCode(): Int {
        var result = brightness
        result = 31 * result + contrast
        result = 31 * result + alpha
        return result
    }

    override fun toString(): String = "GrayColorFilter(brightness=$brightness, contrast=$contrast, alpha=$alpha)"
}
