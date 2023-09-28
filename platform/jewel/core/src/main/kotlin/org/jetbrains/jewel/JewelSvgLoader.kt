package org.jetbrains.jewel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadSvgPainter
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

@Immutable
class JewelSvgLoader(private val svgPatcher: SvgPatcher) : SvgLoader {

    private val cache = ConcurrentHashMap<String, Painter>()
    private val rawSvgCache = ConcurrentHashMap<String, Painter>()

    @Composable
    override fun loadSvgResource(
        svgPath: String,
        resourceLoader: ResourceLoader,
        pathPatcher: @Composable (String) -> String,
    ): Painter {
        val patchedPath = pathPatcher(svgPath)
        cache[patchedPath]?.let { return it }

        val painter = rememberPatchedSvgResource(patchedPath, resourceLoader)
        cache[patchedPath] = painter
        return painter
    }

    @Composable
    private fun rememberPatchedSvgResource(
        resourcePath: String,
        loader: ResourceLoader,
    ): Painter {
        val density = LocalDensity.current

        val painter = useResource(resourcePath, loader) {
            loadSvgPainter(it.patchColors(resourcePath), density)
        }
        return remember(resourcePath, density, loader) { painter }
    }

    @Composable
    override fun loadRawSvg(rawSvg: String, key: String): Painter =
        rawSvg.byteInputStream().use { loadRawSvg(it, key) }

    @Composable
    override fun loadRawSvg(rawSvg: InputStream, key: String): Painter {
        rawSvgCache[key]?.let { return it }

        val painter = rememberRawSvgResource(rawSvg, key)
        cache[key] = painter
        return painter
    }

    @Composable
    private fun rememberRawSvgResource(rawSvg: InputStream, key: String): Painter {
        val density = LocalDensity.current

        val painter = loadSvgPainter(rawSvg, density)
        return remember(key, density) { painter }
    }

    private fun InputStream.patchColors(resourcePath: String): InputStream =
        svgPatcher.patchSvg(this, resourcePath).byteInputStream()

    // Copied from androidx.compose.ui.res.Resources
    private inline fun <T> useResource(
        resourcePath: String,
        loader: ResourceLoader,
        block: (InputStream) -> T,
    ): T = loader.load(resourcePath).use(block)
}
