// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.rendering

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import java.net.URI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.util.myLogger

/**
 * Defines a contract for resolving a raw image source string from Markdown into a fully qualified, loadable path.
 *
 * This is used to allow consumers of Jewel Markdown to control how image paths are resolved. For example, you can use
 * this to resolve relative paths to absolute paths, or to handle custom URI schemes.
 *
 * You can provide an implementation of this interface using the [LocalMarkdownImageSourceResolver] composition local.
 *
 * @see DefaultImageSourceResolver
 * @see LocalMarkdownImageSourceResolver
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public interface ImageSourceResolver {
    /**
     * Resolves a raw image destination string from a Markdown file into a fully-qualified, loadable path.
     *
     * @param rawDestination The raw destination string from the Markdown, e.g., "my-image.png" or
     *   "https://example.com/image.png".
     * @return A fully-qualified, loadable path to the image, which can be consumed by an image loader.
     */
    public fun resolve(rawDestination: String): String
}

/**
 * The default implementation of [ImageSourceResolver].
 *
 * Resolves full URIs as-is and attempts to find relative paths in the current classloader's resources.
 *
 * @see ImageSourceResolver
 */
internal object DefaultImageSourceResolver : ImageSourceResolver {
    override fun resolve(rawDestination: String): String {
        val uri = URI.create(rawDestination)
        if (uri.scheme != null) return rawDestination

        val resourceUrl = javaClass.classLoader.getResource(rawDestination.removePrefix("/"))

        if (resourceUrl == null) {
            myLogger()
                .warn(
                    "Markdown image '$rawDestination' expected at classpath '$rawDestination' but not found. " +
                        "Please ensure it's in your 'src/main/resources/' folder."
                )
            return rawDestination // This will cause Coil to fail and not render anything.
        }

        return resourceUrl.toExternalForm()
    }
}

/**
 * Provides an [ImageSourceResolver] to the composition. You can use this to customize how image sources are resolved in
 * Markdown. You can use this API to resolve images from different classloaders or sources.
 *
 * For example, to resolve images relative to a base URL, you could provide an implementation like this:
 * ```kotlin
 * val baseUrl = "https://example.com/images/"
 * val resolver = object : ImageSourceResolver {
 *     override fun resolve(rawDestination: String): String {
 *         return baseUrl + rawDestination
 *     }
 * }
 *
 * CompositionLocalProvider(LocalMarkdownImageSourceResolver provides resolver) {
 *     MarkdownViewer(markdownText)
 * }
 * ```
 *
 * @see ImageSourceResolver
 * @see DefaultImageSourceResolver
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public val LocalMarkdownImageSourceResolver: ProvidableCompositionLocal<ImageSourceResolver> =
    staticCompositionLocalOf {
        DefaultImageSourceResolver
    }
