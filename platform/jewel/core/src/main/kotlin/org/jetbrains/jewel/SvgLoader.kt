package org.jetbrains.jewel

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import java.io.InputStream

interface SvgLoader {

    /**
     * Creates a [Painter] from the provided [svgPath], using the
     * [resourceLoader] and [pathPatcher] to locate the correct resource
     * file. The icon colors are patched, if needed, and the result is
     * cached in memory.
     *
     * @param svgPath The path to the SVG resource to load.
     * @param resourceLoader The [ResourceLoader] to use to load the resource.
     * @param pathPatcher A function that can be used to patch the path of
     * the resource (e.g., for mapping to New UI icons in the IJ Platform).
     */
    @Composable
    fun loadSvgResource(
        svgPath: String,
        resourceLoader: ResourceLoader,
        pathPatcher: @Composable (String) -> String,
    ): Painter

    /**
     * Creates a [Painter] from the provided [rawSvg], using the [key]
     * to maintain an in-memory cache of the loaded SVG.
     *
     * The [rawSvg] stream is **not** automatically closed after being
     * consumed.
     *
     * Note: when loading raw SVGs, icon color patching is not possible.
     * The SVG contents are not manipulated in any way before loading.
     *
     * @param rawSvg An [InputStream] containing a raw SVG.
     * @param key A unique name for the SVG that is being loaded, used
     * for in-memory caching.
     */
    @Composable
    fun loadRawSvg(rawSvg: InputStream, key: String): Painter

    /**
     * Creates a [Painter] from the provided [rawSvg], using the [key]
     * to maintain an in-memory cache of the loaded SVG.
     *
     * Note: when loading raw SVGs, icon color patching is not possible.
     * The SVG contents are not manipulated in any way before loading.
     *
     * @param rawSvg A [String] containing a raw SVG.
     * @param key A unique name for the SVG that is being loaded, used
     * for in-memory caching.
     */
    @Composable
    fun loadRawSvg(rawSvg: String, key: String): Painter
}
