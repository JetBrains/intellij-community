package org.jetbrains.jewel.samples.standalone.view.component

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
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.ui.component.DropdownLink
import org.jetbrains.jewel.ui.component.ExternalLink
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.separator
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LinkUnderlineBehavior

@Composable
fun Links() {
    val isDark = JewelTheme.isDark
    val alwaysUnderlinedStyle =
        remember(isDark) {
            if (isDark) {
                LinkStyle.dark(underlineBehavior = LinkUnderlineBehavior.ShowAlways)
            } else {
                LinkStyle.light(underlineBehavior = LinkUnderlineBehavior.ShowAlways)
            }
        }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Link("Link", {})

        Link("Always underlined", {}, style = alwaysUnderlinedStyle)

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
        Link("Link", {}, enabled = false)

        Link("Always underlined", {}, style = alwaysUnderlinedStyle, enabled = false)

        ExternalLink("ExternalLink", {}, enabled = false)

        DropdownLink("DropdownLink", enabled = false) {}
    }
}
