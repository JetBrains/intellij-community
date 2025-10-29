// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.rendering

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import java.net.URI
import java.nio.file.Path
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
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
     * @return A fully-qualified, loadable path to the image, which can be consumed by an image loader, or `null` if the
     *   image could not be resolved.
     */
    public fun resolve(rawDestination: String): String?

    public companion object {
        @VisibleForTesting
        internal val defaultCapabilities =
            setOf(
                ResolveCapability.PlainUri,
                ResolveCapability.RelativePathInResources(),
                ResolveCapability.AbsolutePath,
            )

        /**
         * Creates [ImageSourceResolver] that can resolve image links in Markdown files if they are either:
         * - plain URIs, e.g., `https://example.com/image.png` or `file:///image.png`
         * - absolute paths, e.g., `/image.png`
         * - relative paths in the current classloader's resources, e.g., `/images/my-image.png`
         * - relative paths relative to a given root directory [rootDir], e.g., `../images/my-image.png`
         *
         * If [logResolveFailure] is true, logs any failures to resolve image sources.
         */
        public fun create(rootDir: Path, logResolveFailure: Boolean): ImageSourceResolver =
            create(
                buildSet {
                    addAll(defaultCapabilities)
                    add(ResolveCapability.RelativePath(rootDir))
                },
                logResolveFailure,
            )

        /**
         * Creates [ImageSourceResolver] that can resolve image links in Markdown files according to provided
         * [resolveCapabilities].
         *
         * If [logResolveFailure] is true, logs any failures to resolve image sources.
         */
        public fun create(
            resolveCapabilities: Set<ResolveCapability> = defaultCapabilities,
            logResolveFailure: Boolean = true,
        ): ImageSourceResolver = DefaultImageSourceResolver(resolveCapabilities, logResolveFailure)
    }

    /** Provides a list of capabilities that the default [ImageSourceResolver] implementation supports. */
    @ApiStatus.Experimental
    @ExperimentalJewelApi
    public sealed interface ResolveCapability {
        /** Resolves a raw image destination string from a Markdown file into a fully-qualified, loadable path. */
        public fun resolve(rawDestination: String): String?

        /** Represents the ability to resolve plain URIs as-is. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        public object PlainUri : ResolveCapability {
            override fun toString(): String = "PlainUri"

            override fun resolve(rawDestination: String): String? {
                val uri = runCatching { URI.create(rawDestination) }.getOrNull() ?: return null
                return if (uri.isAbsolute) rawDestination else null
            }
        }

        /**
         * Represents the ability to resolve relative paths in the [resourceClass] classloader's resources, or in the
         * current classloader's resources if [resourceClass] is `null`.
         */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        public class RelativePathInResources(private val resourceClass: Class<*>? = null) : ResolveCapability {
            override fun toString(): String = "RelativePathInResources"

            override fun resolve(rawDestination: String): String? =
                (resourceClass ?: javaClass).classLoader.getResource(rawDestination.removePrefix("/"))?.toExternalForm()
        }

        /** Represents the ability to resolve absolute paths as-is. */
        public object AbsolutePath : ResolveCapability {
            override fun resolve(rawDestination: String): String? {
                val rawPath = Path.of(rawDestination)
                if (rawPath.isAbsolute) return rawDestination
                return null
            }
        }

        /** Represents the ability to resolve relative paths relative to a given root directory [rootDir]. */
        @ApiStatus.Experimental
        @ExperimentalJewelApi
        public class RelativePath(private val rootDir: Path) : ResolveCapability {
            override fun resolve(rawDestination: String): String? {
                val rawPath = Path.of(rawDestination)
                // don't resolve absolute paths, it's not this resolver's capability
                if (rawPath.isAbsolute) return null

                val normalizedRoot = runCatching { rootDir.toAbsolutePath().normalize() }.getOrNull() ?: return null

                val resolved = runCatching { normalizedRoot.resolve(rawPath).normalize() }.getOrNull() ?: return null

                return resolved.toString()
            }

            override fun toString(): String = "RelativePath(rootDir=$rootDir)"
        }
    }
}

/**
 * The default implementation of [ImageSourceResolver] that can resolve image links in Markdown files according to
 * provided [resolveCapabilities].
 *
 * @param resolveCapabilities A list of [ImageSourceResolver.ResolveCapability]s that this resolver can support.
 * @param logResolveFailure Whether to log any failures to resolve image sources.
 * @see ImageSourceResolver
 */
internal class DefaultImageSourceResolver(
    private val resolveCapabilities: Set<ImageSourceResolver.ResolveCapability> =
        ImageSourceResolver.defaultCapabilities,
    private val logResolveFailure: Boolean = true,
) : ImageSourceResolver {
    override fun resolve(rawDestination: String): String? {
        val result = resolveCapabilities.firstNotNullOfOrNull { it.resolve(rawDestination) }
        if (result == null && logResolveFailure) {
            myLogger()
                .warn(
                    "Failed to resolve image source: $rawDestination. Supported capabilities: ${resolveCapabilities.joinToString()}"
                )
        }
        return result
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
        ImageSourceResolver.create()
    }
