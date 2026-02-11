// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.icons.ImageResourceLoader
import org.jetbrains.icons.impl.rendering.DefaultImageModifiers
import org.jetbrains.icons.rendering.ImageModifiers
import org.jetbrains.jewel.foundation.InternalJewelApi

@InternalJewelApi
@ApiStatus.Internal
public class PathImageResourceLoader(public val path: String, public val classLoader: ClassLoader?) : ImageResourceLoader {
    public fun loadData(imageModifiers: ImageModifiers?): ByteArray {
        val knownMods = imageModifiers as? DefaultImageModifiers
        val finalPath = applyPathModifiers(path, knownMods)
        val resourceStream = if (classLoader != null) {
            classLoader.getResourceAsStream(finalPath)
        } else ClassLoader.getSystemResourceAsStream(path) 
        return resourceStream?.readBytes() ?: error("Resource not found: $finalPath")
    }
    
    private fun applyPathModifiers(path: String, modifiers: DefaultImageModifiers?): String {
        if (modifiers == null) return path
        return buildString {
            append(path.substringBeforeLast('/', ""))
            append('/')
            append(path.substringBeforeLast('.').substringAfterLast('/'))
            if (modifiers.isDark) {
                append("_dark")
            }
            append('.')
            append(path.substringAfterLast('.'))
        }
    }
}
