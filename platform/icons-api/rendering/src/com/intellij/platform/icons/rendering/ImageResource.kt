// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering

import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.patchers.SvgPatcher

interface ImageModifiers {
    val colorFilter: ColorFilter?
    val svgPatcher: SvgPatcher?
}

interface ImageResource {
    /**
     * Image width in pixels, if the image is rescalable this should return default size or null if default size is not
     * set.
     */
    val width: Int?

    /**
     * Image height in pixels, if the image is rescalable this should return default size or null if default size is not
     * set.
     */
    val height: Int?

    companion object
}
