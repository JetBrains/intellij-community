package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadSvgPainter
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.LocalIsDarkTheme
import org.jetbrains.jewel.SelectableComponentState
import org.jetbrains.jewel.SvgPatcher
import org.jetbrains.jewel.painterResource
import java.io.InputStream

@Immutable
class ResourcePathPainterProvider<T : SelectableComponentState>(
    private val basePath: String,
    private val svgPatcher: SvgPatcher,
    private val prefixTokensProvider: (state: T) -> String = { "" },
    private val suffixTokensProvider: (state: T) -> String = { "" },
) : StatefulPainterProvider<T> {

    @Composable
    override fun getPainter(state: T, resourceLoader: ResourceLoader): State<Painter> {
        val patchedPath = patchPath(state, basePath)
        val isSvg = patchedPath.endsWith(".svg", ignoreCase = true)
        val painter = if (isSvg) {
            rememberPatchedSvgResource(basePath, patchedPath, resourceLoader)
        } else {
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

        if (state.isSelected) {
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

    @Composable
    private fun rememberPatchedSvgResource(
        basePath: String,
        resourcePath: String,
        loader: ResourceLoader,
    ): Painter {
        val density = LocalDensity.current

        val painter = try {
            useResource(resourcePath, loader) {
                loadSvgPainter(it.patchColors(), density)
            }
        } catch (e: IllegalArgumentException) {
            val simplerPath = trySimplifyingPath(resourcePath)
            if (simplerPath != null) {
                System.err.println("Unable to load '$resourcePath' (base: $basePath), trying simpler version: $simplerPath")
                rememberPatchedSvgResource(basePath, simplerPath, loader)
            } else {
                throw IllegalArgumentException("Unable to load '$resourcePath' (base: $basePath), no simpler version available", e)
            }
        }
        return remember(resourcePath, density, loader) { painter }
    }

    private fun trySimplifyingPath(originalPath: String): String? {
        // Step 1: attempt to remove dark qualifier
        val darkIndex = originalPath.lastIndexOf("_dark")
        if (darkIndex > 0) {
            return originalPath.removeRange(darkIndex, darkIndex + "_dark".length)
        }

        // Step 2: attempt to remove density and size qualifiers
        val retinaIndex = originalPath.lastIndexOf("@2x")
        if (retinaIndex > 0) {
            return originalPath.removeRange(retinaIndex, retinaIndex + "@2x".length)
        }

        // TODO remove size qualifiers (e.g., "@20x20")

        // Step 3: attempt to remove extended state qualifiers (pressed, hovered)
        val pressedIndex = originalPath.lastIndexOf("Pressed")
        if (pressedIndex > 0) {
            return originalPath.removeRange(pressedIndex, pressedIndex + "Pressed".length)
        }

        val hoveredIndex = originalPath.lastIndexOf("Hovered")
        if (hoveredIndex > 0) {
            return originalPath.removeRange(hoveredIndex, hoveredIndex + "Hovered".length)
        }

        // Step 4: attempt to remove state qualifiers (indeterminate, selected, focused, disabled)
        val indeterminateIndex = originalPath.lastIndexOf("Indeterminate")
        if (indeterminateIndex > 0) {
            return originalPath.removeRange(indeterminateIndex, indeterminateIndex + "Indeterminate".length)
        }

        val selectedIndex = originalPath.lastIndexOf("Selected")
        if (selectedIndex > 0) {
            return originalPath.removeRange(selectedIndex, selectedIndex + "Selected".length)
        }

        val focusedIndex = originalPath.lastIndexOf("Focused")
        if (focusedIndex > 0) {
            return originalPath.removeRange(focusedIndex, focusedIndex + "Focused".length)
        }

        val disabledIndex = originalPath.lastIndexOf("Disabled")
        if (disabledIndex > 0) {
            return originalPath.removeRange(disabledIndex, disabledIndex + "Disabled".length)
        }

        return null
    }

    private fun InputStream.patchColors(): InputStream =
        svgPatcher.patchSvg(this).byteInputStream()

    // Copied from androidx.compose.ui.res.Resources
    private inline fun <T> useResource(
        resourcePath: String,
        loader: ResourceLoader,
        block: (InputStream) -> T,
    ): T = loader.load(resourcePath).use(block)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResourcePathPainterProvider<*>

        if (basePath != other.basePath) return false
        if (svgPatcher != other.svgPatcher) return false

        return true
    }

    override fun hashCode(): Int {
        var result = basePath.hashCode()
        result = 31 * result + svgPatcher.hashCode()
        return result
    }

    override fun toString() = "ResourcePathPainterProvider(basePath='$basePath', svgPatcher=$svgPatcher)"
}
