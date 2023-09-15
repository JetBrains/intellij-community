package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.ResourceLoader
import org.jetbrains.jewel.IntelliJThemeIconData
import org.jetbrains.jewel.InteractiveComponentState
import org.jetbrains.jewel.InternalJewelApi
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.styling.ResourcePainterProvider
import org.jetbrains.jewel.styling.ResourcePathPatcher
import org.jetbrains.jewel.styling.SimpleResourcePathPatcher
import org.jetbrains.jewel.styling.StatefulResourcePathPatcher

@OptIn(InternalJewelApi::class)
internal class BridgeResourcePainterProvider<T> @InternalJewelApi constructor(
    basePath: String,
    svgLoader: SvgLoader,
    pathPatcher: ResourcePathPatcher<T>,
    private val iconMapper: IconMapper,
    private val iconData: IntelliJThemeIconData,
) : ResourcePainterProvider<T>(basePath, svgLoader, pathPatcher) {

    @Composable
    override fun patchPath(
        basePath: String,
        resourceLoader: ResourceLoader,
        extraData: T?,
    ): String {
        val patchedPath = super.patchPath(basePath, resourceLoader, extraData)
        return iconMapper.mapPath(patchedPath, iconData, resourceLoader)
    }

    companion object Factory {

        fun stateless(basePath: String, svgLoader: SvgLoader, iconData: IntelliJThemeIconData) =
            BridgeResourcePainterProvider<Unit>(
                basePath,
                svgLoader,
                SimpleResourcePathPatcher(),
                BridgeIconMapper,
                iconData,
            )

        fun <T : InteractiveComponentState> stateful(
            iconPath: String,
            svgLoader: SvgLoader,
            iconData: IntelliJThemeIconData,
            prefixTokensProvider: (state: T) -> String = { "" },
            suffixTokensProvider: (state: T) -> String = { "" },
            pathPatcher: ResourcePathPatcher<T> = StatefulResourcePathPatcher(
                prefixTokensProvider,
                suffixTokensProvider,
            ),
        ) =
            BridgeResourcePainterProvider(iconPath, svgLoader, pathPatcher, BridgeIconMapper, iconData)
    }
}
