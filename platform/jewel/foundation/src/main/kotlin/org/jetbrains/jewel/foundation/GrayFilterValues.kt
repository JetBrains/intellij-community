// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
@GenerateDataFunctions
public class GrayFilterValues(public val brightness: Int, public val contrast: Int, public val alpha: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GrayFilterValues

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

    override fun toString(): String {
        return "GrayFilterValues(brightness=$brightness, contrast=$contrast, alpha=$alpha)"
    }

    public companion object
}

public val LocalGrayFilterValues: ProvidableCompositionLocal<GrayFilterValues> = staticCompositionLocalOf {
    error("No GrayFilterValues provided. Have you forgotten the theme?")
}
