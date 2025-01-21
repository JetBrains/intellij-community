// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DropdownLink
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.component.styling.LinkColors
import org.jetbrains.jewel.ui.component.styling.LinkIcons
import org.jetbrains.jewel.ui.component.styling.LinkMetrics
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LinkUnderlineBehavior
import org.jetbrains.jewel.ui.theme.linkStyle

@Composable
public fun Links() {
    val alwaysUnderline = JewelTheme.linkStyle.copy(underlineBehavior = LinkUnderlineBehavior.ShowAlways)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Link(text = "Link", onClick = {})

        Link(text = "Always underlined", onClick = {}, style = alwaysUnderline)

        ExternalLink("ExternalLink", {})

        val items = remember { listOf("Light", "Dark", "---", "High Contrast", "Darcula", "IntelliJ Light") }
        var selected by remember { mutableStateOf(items.first()) }
        DropdownLink("DropdownLink") {
            items.forEach {
                if (it == "---") {
                    separator()
                } else {
                    selectableItem(selected = selected == it, onClick = { selected = it }) { Text(it) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Link(text = "Link", onClick = {}, enabled = false)

        Link(text = "Always underlined", onClick = {}, style = alwaysUnderline, enabled = false)

        ExternalLink(text = "ExternalLink", onClick = {}, enabled = false)

        DropdownLink(text = "DropdownLink", enabled = false) {}
    }
}

private fun LinkStyle.copy(
    colors: LinkColors = this.colors,
    metrics: LinkMetrics = this.metrics,
    icons: LinkIcons = this.icons,
    underlineBehavior: LinkUnderlineBehavior = this.underlineBehavior,
): LinkStyle = LinkStyle(colors, metrics, icons, underlineBehavior)
