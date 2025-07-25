package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.outline
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.ui.typography
import org.jetbrains.skiko.Cursor

@Composable
public fun Borders(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GroupHeader("Group header")
        Text("This is a group header example")

        Spacer(Modifier.height(8.dp))

        var open by remember { mutableStateOf(false) }
        val interactionSource = remember { MutableInteractionSource() }
        GroupHeader(
            text = "Group header with startComponent",
            modifier =
                Modifier.clickable(indication = null, interactionSource = interactionSource) { open = !open }
                    .hoverable(interactionSource)
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))),
            startComponent = {
                if (open) {
                    Icon(AllIconsKeys.General.ChevronDown, "Chevron")
                } else {
                    Icon(AllIconsKeys.General.ChevronRight, "Chevron")
                }
            },
        )
        if (open) {
            Text("Surprise! 👻", Modifier.padding(start = 24.dp))
        }

        Spacer(Modifier.height(8.dp))

        GroupHeader(
            "Group header with both components",
            startComponent = { Icon(AllIconsKeys.General.Information, contentDescription = null) },
            endComponent = {
                Text(
                    "End component",
                    style = JewelTheme.typography.small,
                    modifier = Modifier.outline(Outline.Warning, focused = true).padding(4.dp, 2.dp),
                )
            },
        )
        Spacer(Modifier.height(8.dp))

        BordersTester()
    }
}

@Composable
private fun BordersTester(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GroupHeader("Border alignment/expand")
        var borderAlignment by remember { mutableStateOf(Stroke.Alignment.Center) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButtonRow(
                text = "Inside",
                selected = borderAlignment == Stroke.Alignment.Inside,
                onClick = { borderAlignment = Stroke.Alignment.Inside },
            )
            RadioButtonRow(
                text = "Center",
                selected = borderAlignment == Stroke.Alignment.Center,
                onClick = { borderAlignment = Stroke.Alignment.Center },
            )
            RadioButtonRow(
                text = "Outside",
                selected = borderAlignment == Stroke.Alignment.Outside,
                onClick = { borderAlignment = Stroke.Alignment.Outside },
            )
        }
        var width by remember { mutableStateOf(1.dp) }
        var expand by remember { mutableStateOf(0.dp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton({ width += 1.dp }) { Text("+width") }
            OutlinedButton({ width -= 1.dp }, enabled = width > 1.dp) { Text("-width") }
            OutlinedButton({ expand += 1.dp }) { Text("+expand") }
            OutlinedButton({ expand -= 1.dp }) { Text("-expand") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val isDark = JewelTheme.isDark
            val colorPalette = JewelTheme.colorPalette
            val borderColor =
                if (isDark) {
                    colorPalette.blueOrNull(6) ?: Color(0xFF3574F0)
                } else {
                    colorPalette.blueOrNull(4) ?: Color(0xFF3574F0)
                }
            val backgroundColor =
                if (isDark) {
                    colorPalette.grayOrNull(4) ?: Color(0xFF43454A)
                } else {
                    colorPalette.grayOrNull(11) ?: Color(0xFFDFE1E5)
                }

            Box(
                Modifier.size(28.dp, 28.dp)
                    .background(backgroundColor, shape = CircleShape)
                    .border(borderAlignment, width, borderColor, CircleShape, expand)
            )
            Box(
                Modifier.size(72.dp, 28.dp)
                    .background(backgroundColor, shape = RectangleShape)
                    .border(borderAlignment, width, borderColor, RectangleShape, expand)
            )
            Box(
                Modifier.size(72.dp, 28.dp)
                    .background(backgroundColor, shape = RoundedCornerShape(4.dp))
                    .border(borderAlignment, width, borderColor, RoundedCornerShape(4.dp), expand)
            )
            Box(
                Modifier.size(72.dp, 28.dp)
                    .background(backgroundColor, shape = RoundedCornerShape(4.dp, 0.dp, 4.dp, 0.dp))
                    .border(borderAlignment, width, borderColor, RoundedCornerShape(4.dp, 0.dp, 4.dp, 0.dp), expand)
            )
        }
    }
}
