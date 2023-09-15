package org.jetbrains.jewel

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.ResourceLoader

interface SvgLoader {

    @Composable
    fun loadSvgResource(
        svgPath: String,
        resourceLoader: ResourceLoader,
        pathPatcher: @Composable (String) -> String,
    ): Painter
}
