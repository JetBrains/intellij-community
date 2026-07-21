// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.rendering.ImageModifiers
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.InternalJewelApi

// TODO: Replace with ModuleImageResourceLocation and custom Loader for it
/**
 * An [ImageResourceLocation] that loads image bytes from a classpath resource at [path] using [classLoader], applying
 * dark-mode path modifiers.
 */
@InternalJewelApi
@ApiStatus.Internal
public class PathImageResourceLocation(public val path: String, public val classLoader: ClassLoader?) :
    ImageResourceLocation {
    /**
     * Loads and returns the raw bytes of the image resource, applying dark-mode path modifiers from [imageModifiers] if
     * present.
     *
     * @param imageModifiers Optional image modifiers (e.g., dark mode) used to select the correct resource path.
     */
    public fun loadData(imageModifiers: ImageModifiers?): ByteArray {
        val knownMods = imageModifiers as? DefaultImageModifiers
        val finalPath = applyPathModifiers(path, knownMods)
        val resourceStream =
            if (classLoader != null) {
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
            // A stroked icon loads its light artwork even in a dark theme, matching the Swing frontend: the stroke
            // palette describes the light variants, so a `_dark` variant would come back with colors it does not know
            // and would be left in its authored color instead of being recolored.
            if (modifiers.isDark && modifiers.stroke == null) {
                append("_dark")
            }
            append('.')
            append(path.substringAfterLast('.'))
        }
    }
}
