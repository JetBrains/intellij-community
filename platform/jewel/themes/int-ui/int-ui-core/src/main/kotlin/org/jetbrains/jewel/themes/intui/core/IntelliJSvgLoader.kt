package org.jetbrains.jewel.themes.intui.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.res.loadSvgPainter
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.SvgPatcher
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

@Immutable
class IntelliJSvgLoader(private val svgPatcher: SvgPatcher) : SvgLoader {

    private val cache = ConcurrentHashMap<String, Painter>()

    @Composable
    override fun loadSvgResource(
        originalPath: String,
        resourceLoader: ResourceLoader,
        pathPatcher: @Composable (String) -> String,
    ): Painter {
        val patchedPath = pathPatcher(originalPath)
        cache[patchedPath]?.let { return it }

        val painter = rememberPatchedSvgResource(originalPath, patchedPath, resourceLoader)
        cache[patchedPath] = painter
        return painter
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
                throw IllegalArgumentException(
                    "Unable to load '$resourcePath' (base: $basePath), no simpler version available",
                    e
                )
            }
        }
        return remember(resourcePath, density, loader) { painter }
    }

    private fun trySimplifyingPath(originalPath: String): String? {
        // Step 1: attempt to remove extended state qualifiers (pressed, hovered)
        val pressedIndex = originalPath.lastIndexOf("Pressed")
        if (pressedIndex > 0) {
            return originalPath.removeRange(pressedIndex, pressedIndex + "Pressed".length)
        }

        val hoveredIndex = originalPath.lastIndexOf("Hovered")
        if (hoveredIndex > 0) {
            return originalPath.removeRange(hoveredIndex, hoveredIndex + "Hovered".length)
        }

        // Step 2: attempt to remove state qualifiers (indeterminate, selected, focused, disabled)
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

        // Step 3: attempt to remove density and size qualifiers
        val retinaIndex = originalPath.lastIndexOf("@2x")
        if (retinaIndex > 0) {
            return originalPath.removeRange(retinaIndex, retinaIndex + "@2x".length)
        }

        // Step 4: attempt to remove dark qualifier
        val darkIndex = originalPath.lastIndexOf("_dark")
        if (darkIndex > 0) {
            return originalPath.removeRange(darkIndex, darkIndex + "_dark".length)
        }

        // TODO remove size qualifiers (e.g., "@20x20")

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
}
