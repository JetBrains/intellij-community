package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Badge
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.BadgeColors
import org.jetbrains.jewel.ui.component.styling.BadgeStyle
import org.jetbrains.jewel.ui.theme.badgeStyle

@Composable
public fun Badges(modifier: Modifier = Modifier) {
    VerticallyScrollableContainer(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GroupHeader("Basic")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge { Text("Default") }

                Badge(style = JewelTheme.badgeStyle.new) { Text("New") }

                Badge(style = JewelTheme.badgeStyle.beta) { Text("Beta") }

                Badge(style = JewelTheme.badgeStyle.free) { Text("Free") }

                Badge(style = JewelTheme.badgeStyle.trial) { Text("Trial") }

                Badge(style = JewelTheme.badgeStyle.information) { Text("Information") }
            }

            GroupHeader("Clickable")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var clickCount by remember { mutableIntStateOf(0) }
                Badge(onClick = { clickCount++ }) { Text("Clicks: $clickCount") }

                Badge(onClick = { println("Badge clicked!") }) { Text("Action") }
            }

            GroupHeader("Disabled")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Badge(enabled = false) { Text("Disabled") } }

            GroupHeader("Tooltip")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // TODO: fix the placement to be above the badge, following the spacing constraints

                val placement by remember {
                    mutableStateOf<TooltipPlacement>(
                        TooltipPlacement.ComponentRect(
                            anchor = Alignment.TopStart,
                            alignment = Alignment.TopEnd,
                            DpOffset(x = (-12).dp, y = (-4).dp),
                        )
                    )
                }

                Tooltip(tooltip = { Text("Tooltip text") }, tooltipPlacement = placement) {
                    Badge { Text("Hover me!") }
                }
            }

            GroupHeader("Gradient Background")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val background =
                    Brush.horizontalGradient(
                        tileMode = TileMode.Repeated,
                        colors = listOf(Color(0xFFE063CC), Color(0xFF8D7BFD), Color(0xFF8F74F9)),
                    )

                Badge(
                    style =
                        BadgeStyle(
                            colors =
                                BadgeColors(
                                    background = background,
                                    backgroundDisabled = SolidColor(Color.Gray),
                                    backgroundFocused = background,
                                    backgroundPressed = background,
                                    backgroundHovered = background,
                                    content = Color.White,
                                    contentDisabled = Color.Gray,
                                    contentFocused = Color.White,
                                    contentPressed = Color.White,
                                    contentHovered = Color.White,
                                ),
                            metrics = JewelTheme.badgeStyle.default.metrics,
                        )
                ) {
                    Text("Ultimate")
                }
            }
        }
    }
}
