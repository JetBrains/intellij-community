// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.badge.DotBadgeShape
import org.jetbrains.jewel.ui.painter.hints.Badge
import org.jetbrains.jewel.ui.painter.hints.Size
import org.jetbrains.jewel.ui.painter.hints.Stroke
import org.jetbrains.jewel.ui.theme.colorPalette

@Composable
public fun Icons() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(ShowcaseIcons.jewelLogo, null, Modifier.size(16.dp))
        Icon(ShowcaseIcons.jewelLogo, "Jewel Logo", Modifier.size(32.dp))
        Icon(ShowcaseIcons.jewelLogo, "Jewel Logo", Modifier.size(64.dp))
        Icon(ShowcaseIcons.jewelLogo, "Jewel Logo", Modifier.size(128.dp))
        Icon(
            key = ShowcaseIcons.jewelLogo,
            contentDescription = "Jewel Logo",
            modifier = Modifier.size(128.dp),
            colorFilter = ColorFilter.tint(Color.Magenta, BlendMode.Multiply),
        )
    }

    Text("Hints:")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(key = AllIconsKeys.Nodes.ConfigFolder, contentDescription = "taskGroup")
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                key = AllIconsKeys.Nodes.ConfigFolder,
                contentDescription = "taskGroup",
                hint = Badge(Color.Red, DotBadgeShape.Default),
            )
        }
        val backgroundColor =
            if (JewelTheme.isDark) {
                JewelTheme.colorPalette.blueOrNull(4) ?: Color(0xFF375FAD)
            } else {
                JewelTheme.colorPalette.blueOrNull(4) ?: Color(0xFF3574F0)
            }
        Box(
            Modifier.size(24.dp).background(backgroundColor, shape = RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(key = AllIconsKeys.Nodes.ConfigFolder, contentDescription = "taskGroup", hint = Stroke(Color.White))
        }
        Box(
            Modifier.size(24.dp).background(backgroundColor, shape = RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                key = AllIconsKeys.Nodes.ConfigFolder,
                contentDescription = "taskGroup",
                hints = arrayOf(Stroke(Color.White), Badge(Color.Red, DotBadgeShape.Default)),
            )
        }
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(key = AllIconsKeys.Nodes.ConfigFolder, contentDescription = "taskGroup", hint = Size(20))
        }
    }
}
