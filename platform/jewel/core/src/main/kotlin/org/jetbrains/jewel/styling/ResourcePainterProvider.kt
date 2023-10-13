package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader
import org.jetbrains.jewel.IconMapper
import org.jetbrains.jewel.IntelliJIconMapper
import org.jetbrains.jewel.IntelliJThemeIconData
import org.jetbrains.jewel.InteractiveComponentState
import org.jetbrains.jewel.InternalJewelApi
import org.jetbrains.jewel.LocalIconData
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.painterResource

open class ResourcePainterProvider<T> @InternalJewelApi constructor(
    private val basePath: String,
    private val svgLoader: SvgLoader,
    private val iconMapper: IconMapper,
    private val iconData: IntelliJThemeIconData,
    private val pathPatcher: ResourcePathPatcher<T>,
) : PainterProvider<T> {

    @Composable
    override fun getPainter(resourceLoader: ResourceLoader, extraData: T?): State<Painter> {
        val isSvg = basePath.endsWith(".svg", ignoreCase = true)
        val painter = if (isSvg) {
            svgLoader.loadSvgResource(basePath, resourceLoader) {
                patchPath(basePath, resourceLoader, extraData)
            }
        } else {
            val patchedPath = patchPath(basePath, resourceLoader, extraData)
            painterResource(patchedPath, resourceLoader)
        }

        return rememberUpdatedState(painter)
    }

    @Composable
    protected open fun patchPath(
        basePath: String,
        resourceLoader: ResourceLoader,
        extraData: T?,
    ): String {
        val patched = pathPatcher.patchVariant(basePath, resourceLoader, extraData)
        val override = iconMapper.mapPath(patched, iconData, resourceLoader)
        return pathPatcher.patchTheme(override, resourceLoader)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResourcePainterProvider<*>

        if (basePath != other.basePath) return false
        if (svgLoader != other.svgLoader) return false
        if (iconMapper != other.iconMapper) return false
        if (iconData != other.iconData) return false
        if (pathPatcher != other.pathPatcher) return false

        return true
    }

    override fun hashCode(): Int {
        var result = basePath.hashCode()
        result = 31 * result + svgLoader.hashCode()
        result = 31 * result + iconMapper.hashCode()
        result = 31 * result + iconData.hashCode()
        result = 31 * result + pathPatcher.hashCode()
        return result
    }

    override fun toString(): String =
        "ResourcePainterProvider(basePath='$basePath', svgLoader=$svgLoader, iconMapper=$iconMapper, iconData=$iconData, pathPatcher=$pathPatcher)"

    @OptIn(InternalJewelApi::class) // These are the public constructors
    companion object Factory {

        fun stateless(basePath: String, svgLoader: SvgLoader, iconData: IntelliJThemeIconData) =
            ResourcePainterProvider<Unit>(
                basePath,
                svgLoader,
                IntelliJIconMapper,
                iconData,
                SimpleResourcePathPatcher(),
            )

        fun <T : InteractiveComponentState> stateful(
            basePath: String,
            svgLoader: SvgLoader,
            iconData: IntelliJThemeIconData,
            pathPatcher: ResourcePathPatcher<T> = StatefulResourcePathPatcher(),
        ) =
            ResourcePainterProvider(basePath, svgLoader, IntelliJIconMapper, iconData, pathPatcher)
    }
}

@Composable
fun rememberStatelessPainterProvider(
    basePath: String,
    svgLoader: SvgLoader,
    iconData: IntelliJThemeIconData = LocalIconData.current,
): ResourcePainterProvider<Unit> = remember(basePath, iconData) {
    ResourcePainterProvider.stateless(basePath, svgLoader, iconData)
}

@Composable
fun <T : InteractiveComponentState> rememberStatefulPainterProvider(
    basePath: String,
    svgLoader: SvgLoader,
    pathPatcher: ResourcePathPatcher<T> = StatefulResourcePathPatcher(),
    iconData: IntelliJThemeIconData = LocalIconData.current,
): ResourcePainterProvider<T> = remember(basePath, iconData, pathPatcher) {
    ResourcePainterProvider.stateful(basePath, svgLoader, iconData, pathPatcher)
}
