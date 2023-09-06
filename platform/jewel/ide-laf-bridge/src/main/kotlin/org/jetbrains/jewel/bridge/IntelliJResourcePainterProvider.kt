package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.ResourceLoader
import org.jetbrains.jewel.InteractiveComponentState
import org.jetbrains.jewel.InternalJewelApi
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.styling.ResourcePainterProvider
import org.jetbrains.jewel.styling.ResourcePathPatcher
import org.jetbrains.jewel.styling.SimpleResourcePathPatcher
import org.jetbrains.jewel.styling.StatefulResourcePathPatcher

@OptIn(InternalJewelApi::class)
class IntelliJResourcePainterProvider<T> @InternalJewelApi constructor(
    basePath: String,
    svgLoader: SvgLoader,
    pathPatcher: ResourcePathPatcher<T>,
    private val iconMapper: IconMapper,
) : ResourcePainterProvider<T>(basePath, svgLoader, pathPatcher) {

    @Composable
    override fun patchPath(basePath: String, resourceLoader: ResourceLoader, extraData: T?): String {
        val patchedPath = super.patchPath(basePath, resourceLoader, extraData)
        return iconMapper.mapPath(patchedPath, resourceLoader)
    }

    companion object {

        fun stateless(basePath: String, svgLoader: SvgLoader) =
            IntelliJResourcePainterProvider<Unit>(basePath, svgLoader, SimpleResourcePathPatcher(), IntelliJIconMapper)

        fun <T : InteractiveComponentState> stateful(
            basePath: String,
            svgLoader: SvgLoader,
            pathPatcher: ResourcePathPatcher<T> = StatefulResourcePathPatcher(),
        ) =
            IntelliJResourcePainterProvider(basePath, svgLoader, pathPatcher, IntelliJIconMapper)
    }
}
