package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.ResourceLoader
import org.jetbrains.jewel.LocalIsDarkTheme

open class SimpleResourcePathPatcher<T> : ResourcePathPatcher<T> {

    @Composable
    final override fun patchVariant(basePath: String, resourceLoader: ResourceLoader, extraData: T?) =
        buildString {
            append(basePath.substringBeforeLast('/', ""))
            append('/')
            append(basePath.substringBeforeLast('.').substringAfterLast('/'))

            append(injectVariantTokens(extraData))

            append('.')
            append(basePath.substringAfterLast('.'))
        }

    @Composable
    final override fun patchTheme(basePath: String, resourceLoader: ResourceLoader): String = buildString {
        append(basePath.substringBeforeLast('/', ""))
        append('/')
        append(basePath.substringBeforeLast('.').substringAfterLast('/'))

        // TODO load HiDPI rasterized images ("@2x")
        // TODO load sized SVG images (e.g., "@20x20")

        if (LocalIsDarkTheme.current) {
            append("_dark")
        }

        append('.')
        append(basePath.substringAfterLast('.'))
    }

    @Composable
    protected open fun injectVariantTokens(extraData: T? = null): String = ""
}
