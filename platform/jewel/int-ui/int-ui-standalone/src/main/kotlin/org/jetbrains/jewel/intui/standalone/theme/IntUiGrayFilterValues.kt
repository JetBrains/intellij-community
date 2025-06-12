// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.theme

import org.jetbrains.jewel.foundation.GrayFilterValues

public fun GrayFilterValues.Companion.light(
    brightness: Int = 33,
    contrast: Int = -35,
    alpha: Int = 100,
): GrayFilterValues = GrayFilterValues(brightness, contrast, alpha)

public fun GrayFilterValues.Companion.dark(
    brightness: Int = -70,
    contrast: Int = -70,
    alpha: Int = 100,
): GrayFilterValues = GrayFilterValues(brightness, contrast, alpha)
