// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.rendering

import com.intellij.platform.icons.ImageResourceLocation

interface GenericImageResourceLoader {
    fun loadGenericImage(location: ImageResourceLocation, imageModifiers: ImageModifiers? = null): ImageResource?
}

@Suppress("UNCHECKED_CAST")
interface ImageResourceLoader<TLocation : ImageResourceLocation> : GenericImageResourceLoader {
    override fun loadGenericImage(location: ImageResourceLocation, imageModifiers: ImageModifiers?): ImageResource? =
        loadImage(location as? TLocation ?: error("Unsupported image resource location."), imageModifiers)

    fun loadImage(location: TLocation, imageModifiers: ImageModifiers? = null): ImageResource?
}
