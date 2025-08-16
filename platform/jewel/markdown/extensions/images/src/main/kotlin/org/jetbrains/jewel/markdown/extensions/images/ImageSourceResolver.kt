// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import coil3.toUri
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.util.JewelLogger

/** Defines a contract for resolving a raw image source string from Markdown into a fully-qualified, loadable path. */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface ImageSourceResolver {
    public fun resolve(rawDestination: String): String
}

/**
 * The default implementation of [ImageSourceResolver].
 *
 * Resolves full URIs as-is and attempts to find relative paths in the application's classpath resources.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public class DefaultImageSourceResolver : ImageSourceResolver {
    /**
     * This function checks if the destination is already a full URI (e.g., `http://...`). If not, it assumes the
     * destination is a local resource path and attempts to resolve it using the ClassLoader.
     *
     * @param rawDestination The raw image source string from the Markdown document.
     * @return A string representing a full URI that Coil can load (e.g., jar:file//<path>). If the resource is not
     *   found, it returns the original destination string, which will likely cause Coil to fail and not render
     *   anything.
     */
    override fun resolve(rawDestination: String): String {
        val uri = rawDestination.toUri()

        if (uri.scheme != null) return rawDestination

        val resourceUrl =
            this@DefaultImageSourceResolver::class.java.classLoader.getResource(rawDestination.removePrefix("/"))

        if (resourceUrl == null) {
            JewelLogger.getInstance("Jewel")
                .warn(
                    "Markdown image '$rawDestination' expected at classpath '$rawDestination' but not found. " +
                        "Please ensure it's in your 'src/main/resources/' folder."
                )
            return rawDestination // This will cause Coil to fail and not render anything.
        }

        return resourceUrl.toExternalForm()
    }
}

@ApiStatus.Experimental
@ExperimentalJewelApi
public val LocalMarkdownImageSourceResolver: ProvidableCompositionLocal<ImageSourceResolver> =
    staticCompositionLocalOf {
        error("No LocalMarkdownImageSourceResolver provided. Have you forgotten the theme?")
    }
