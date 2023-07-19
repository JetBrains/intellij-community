package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadSvgPainter
import org.jetbrains.jewel.SvgPatcher
import org.jetbrains.jewel.painterResource
import java.io.InputStream

abstract class BaseResourcePainterProvider<T>(private val svgPatcher: SvgPatcher? = null) : StatefulPainterProvider<T> {

    abstract val normal: String
    abstract val disabled: String
    abstract val focused: String
    abstract val pressed: String
    abstract val hovered: String

    @Composable
    override fun getPainter(state: T, resourceLoader: ResourceLoader): State<Painter> {
        val iconPath = selectVariant(state)
        // TODO check for icon state-based variants (based on file names && dark/density variants)

        val isSvg = iconPath.endsWith(".svg", ignoreCase = true)
        val painter = if (isSvg && svgPatcher != null) {
            rememberPatchedSvgResource(iconPath, resourceLoader, svgPatcher)
        } else {
            painterResource(iconPath, resourceLoader)
        }

        return rememberUpdatedState(painter)
    }

    @Composable
    private fun rememberPatchedSvgResource(
        resourcePath: String,
        loader: ResourceLoader,
        patcher: SvgPatcher,
    ): Painter {
        val density = LocalDensity.current

        return remember(resourcePath, density, loader) {
            useResource(resourcePath, loader) {
                loadSvgPainter(it.patchColors(patcher), density)
            }
        }
    }

    private fun InputStream.patchColors(patcher: SvgPatcher): InputStream =
        patcher.patchSvg(this).byteInputStream()

    // Copied from androidx.compose.ui.res.Resources
    private inline fun <T> useResource(
        resourcePath: String,
        loader: ResourceLoader,
        block: (InputStream) -> T,
    ): T = loader.load(resourcePath).use(block)

    @Composable
    protected abstract fun selectVariant(state: T): String
}
