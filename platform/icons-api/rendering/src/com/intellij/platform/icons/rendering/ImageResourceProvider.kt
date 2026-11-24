// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering

import com.intellij.platform.icons.ImageResourceLocation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ImageResourceProvider {
    fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers? = null): ImageResource

    /**
     * Loads the image with the theme inputs of [theme], for example its color palette and its global filter. The
     * provider reads them from [theme] when it loads the image. A provider that does not apply theme inputs loads the
     * image as [loadImage] without a theme does.
     */
    fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?, theme: ThemeContext): ImageResource =
        loadImage(location, imageModifiers)
}
