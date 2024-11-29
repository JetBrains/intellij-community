package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.theme.colorPalette

@Composable
internal fun Borders() {
    GroupHeader("Group header")
    Text("This is a group header example")

    Spacer(Modifier.height(16.dp))

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
        val borderColor = remember(isDark) { if (isDark) colorPalette.blue(6) else colorPalette.blue(4) }
        val backgroundColor = remember(isDark) { if (isDark) colorPalette.gray(4) else colorPalette.gray(11) }

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
