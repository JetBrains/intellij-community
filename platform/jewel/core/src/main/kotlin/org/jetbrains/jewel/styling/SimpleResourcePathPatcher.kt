package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.ResourceLoader
import org.jetbrains.jewel.LocalIsDarkTheme

open class SimpleResourcePathPatcher<T> : ResourcePathPatcher<T> {

    @Composable
    final override fun patchPath(basePath: String, resourceLoader: ResourceLoader, extraData: T?) =
        buildString {
            append(basePath.substringBeforeLast('/', ""))
            append('/')
            append(basePath.substringBeforeLast('.').substringAfterLast('/'))

            append(injectAdditionalTokens(extraData))

            // TODO load HiDPI rasterized images ("@2x")
            // TODO load sized SVG images (e.g., "@20x20")

            if (LocalIsDarkTheme.current) {
                append("_dark")
            }
            append('.')
            append(basePath.substringAfterLast('.'))
        }

    @Composable
    protected open fun injectAdditionalTokens(extraData: T? = null): String = ""
}
