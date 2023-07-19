package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.InteractiveComponentState
import org.jetbrains.jewel.LocalIsDarkTheme
import org.jetbrains.jewel.SelectableComponentState
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.painterResource

@Immutable
class ResourcePainterProvider<T : InteractiveComponentState>(
    private val basePath: String,
    private val svgLoader: SvgLoader,
    private val prefixTokensProvider: (state: T) -> String = { "" },
    private val suffixTokensProvider: (state: T) -> String = { "" },
) : StatefulPainterProvider<T> {

    @Composable
    override fun getPainter(state: T, resourceLoader: ResourceLoader): State<Painter> {
        val isSvg = basePath.endsWith(".svg", ignoreCase = true)
        val painter = if (isSvg) {
            svgLoader.loadSvgResource(basePath, resourceLoader) { patchPath(state, basePath) }
        } else {
            val patchedPath = patchPath(state, basePath)
            painterResource(patchedPath, resourceLoader)
        }

        return rememberUpdatedState(painter)
    }

    @Composable
    private fun patchPath(state: T, basePath: String): String = buildString {
        append(basePath.substringBeforeLast('/', ""))
        append('/')
        append(basePath.substringBeforeLast('.').substringAfterLast('/'))
        append(prefixTokensProvider(state))

        if (state is SelectableComponentState && state.isSelected) {
            append("Selected")
        }

        if (state.isEnabled) {
            when {
                state.isFocused -> append("Focused")
                !IntelliJTheme.isSwingCompatMode && state.isPressed -> append("Pressed")
                !IntelliJTheme.isSwingCompatMode && state.isHovered -> append("Hovered")
            }
        } else {
            append("Disabled")
        }

        append(suffixTokensProvider(state))

        // TODO load HiDPI rasterized images ("@2x")
        // TODO load sized SVG images (e.g., "@20x20")

        if (LocalIsDarkTheme.current) {
            append("_dark")
        }
        append('.')
        append(basePath.substringAfterLast('.'))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResourcePainterProvider<*>

        if (basePath != other.basePath) return false
        if (svgLoader != other.svgLoader) return false

        return true
    }

    override fun hashCode(): Int {
        var result = basePath.hashCode()
        result = 31 * result + svgLoader.hashCode()
        return result
    }

    override fun toString() = "ResourcePathPainterProvider(basePath='$basePath', svgLoader=$svgLoader)"
}
