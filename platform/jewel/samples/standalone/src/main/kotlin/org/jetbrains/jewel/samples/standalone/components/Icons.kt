package org.jetbrains.jewel.samples.standalone.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.samples.standalone.StandaloneSampleIcons
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider

@Composable
internal fun Icons() {
    GroupHeader("Icons")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val iconProvider = rememberResourcePainterProvider("icons/jewel-logo.svg", StandaloneSampleIcons::class.java)
        val logo by iconProvider.getPainter()

        Icon(logo, "Jewel Logo", Modifier.size(16.dp))
        Icon(logo, "Jewel Logo", Modifier.size(32.dp))
        Icon(logo, "Jewel Logo", Modifier.size(64.dp))
        Icon(logo, "Jewel Logo", Modifier.size(128.dp))
        Icon(
            logo,
            "Jewel Logo",
            ColorFilter.tint(Color.Magenta, BlendMode.Multiply),
            Modifier.size(128.dp),
        )
    }
}
