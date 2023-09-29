package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GroupHeader
import org.jetbrains.jewel.Icon
import org.jetbrains.jewel.SvgLoader
import org.jetbrains.jewel.styling.ResourcePainterProvider

@Composable
internal fun Icons(svgLoader: SvgLoader, resourceLoader: ResourceLoader) {
    GroupHeader("Icons")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val jewelLogoProvider = remember { ResourcePainterProvider.stateless("icons/jewel-logo.svg", svgLoader) }
        val jewelLogo by jewelLogoProvider.getPainter(resourceLoader)

        Icon(jewelLogo, "Jewel Logo", Modifier.size(16.dp))
        Icon(jewelLogo, "Jewel Logo", Modifier.size(32.dp))
        Icon(jewelLogo, "Jewel Logo", Modifier.size(64.dp))
        Icon(jewelLogo, "Jewel Logo", Modifier.size(128.dp))
        Icon(jewelLogo, "Jewel Logo", ColorFilter.tint(Color.Magenta, BlendMode.Multiply), Modifier.size(128.dp))
    }
}
