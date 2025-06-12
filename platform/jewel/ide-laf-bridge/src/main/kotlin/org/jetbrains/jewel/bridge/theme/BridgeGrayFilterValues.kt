// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import com.intellij.util.ui.GrayFilter
import com.intellij.util.ui.UIUtil
import org.jetbrains.jewel.foundation.GrayFilterValues

public fun GrayFilterValues.Companion.readFromLaF(isDark: Boolean): GrayFilterValues {
    val grayFilter = UIUtil.getGrayFilter()
    val (brightness, contrast, alpha) =
        if (grayFilter is GrayFilter) {
            arrayOf(grayFilter.brightness, grayFilter.contrast, grayFilter.alpha)
        } else {
            if (isDark) arrayOf(-70, -70, 100) else arrayOf(33, -35, 100)
        }

    return GrayFilterValues(brightness, contrast, alpha)
}
