// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering

import com.intellij.platform.icons.ImageResourceLocation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ImageResourceProvider {
    fun loadImage(location: ImageResourceLocation, imageModifiers: ImageModifiers? = null): ImageResource
}
