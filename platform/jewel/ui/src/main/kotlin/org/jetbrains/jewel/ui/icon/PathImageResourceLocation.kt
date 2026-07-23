// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.impl.patchers.AUTHORED_STROKE_VARIANT_SUFFIX
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
    public fun loadData(imageModifiers: ImageModifiers?): ByteArray = resolve(imageModifiers).data

    /** Resolves which of this location's files [imageModifiers] calls for, and reads it. */
    internal fun resolve(imageModifiers: ImageModifiers?): ResolvedImageResource {
        val knownMods =
            imageModifiers as? DefaultImageModifiers
                ?: return ResolvedImageResource(
                    data = readOrNull(path) ?: error("Resource not found: $path"),
                    isAuthoredStrokeVariant = false,
                )
        return resolve(stroked = knownMods.stroke != null, isDark = knownMods.isDark)
    }

    /**
     * Resolves the file a stroked or dark icon is read from.
     *
     * A stroked icon prefers its hand-authored stroke variant, which is a separate drawing of the same glyph rather
     * than a recolor of this one, and falls back to the base file for the icons that ship no such variant. Either way
     * it loads light artwork, matching the Swing frontend: the stroke palette describes the light variants, so a
     * `_dark` variant would come back with colors it does not know and would be left in its authored color instead of
     * being recolored.
     */
    internal fun resolve(stroked: Boolean, isDark: Boolean): ResolvedImageResource {
        if (stroked) {
            val strokePath = applyPathModifiers(path, AUTHORED_STROKE_VARIANT_SUFFIX)
            val strokeData = readOrNull(strokePath)
            if (strokeData != null) return ResolvedImageResource(strokeData, isAuthoredStrokeVariant = true)
        }

        val finalPath = applyPathModifiers(path, if (isDark && !stroked) "_dark" else "")
        val data = readOrNull(finalPath) ?: error("Resource not found: $finalPath")
        return ResolvedImageResource(data, isAuthoredStrokeVariant = false)
    }

    private fun applyPathModifiers(path: String, suffix: String): String = buildString {
        append(path.substringBeforeLast('/', ""))
        append('/')
        append(path.substringBeforeLast('.').substringAfterLast('/'))
        append(suffix)
        append('.')
        append(path.substringAfterLast('.'))
    }

    private fun readOrNull(path: String): ByteArray? {
        val resourceStream =
            if (classLoader != null) {
                classLoader.getResourceAsStream(path)
            } else ClassLoader.getSystemResourceAsStream(path)
        return resourceStream?.use { it.readBytes() }
    }
}

/** The bytes a [PathImageResourceLocation] resolved to, and which of its files they came from. */
internal class ResolvedImageResource(val data: ByteArray, val isAuthoredStrokeVariant: Boolean)
