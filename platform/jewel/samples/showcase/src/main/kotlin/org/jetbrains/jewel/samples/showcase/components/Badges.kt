package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Badge
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.BadgeColors
import org.jetbrains.jewel.ui.component.styling.BadgeMetrics
import org.jetbrains.jewel.ui.component.styling.BadgeStyle
import org.jetbrains.jewel.ui.theme.badgeStyle

@Composable
public fun Badges(modifier: Modifier = Modifier) {
    VerticallyScrollableContainer(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GroupHeader("Basic Badges (Non-clickable)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(text = "New")
                Badge(text = "Beta")
                Badge(text = "Preview")
                Badge(text = "Experimental")
            }

            GroupHeader("Clickable Badges")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var clickCount by remember { mutableIntStateOf(0) }
                Badge(text = "Clicks: $clickCount", onClick = { clickCount++ })

                Badge(text = "Action", onClick = { println("Badge clicked!") })
            }

            GroupHeader("Disabled Badges")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(text = "Disabled", enabled = false)

                Badge(text = "Disabled Clickable", enabled = false, onClick = { println("This won't print") })
            }

            GroupHeader("Custom Background Colors")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(
                    text = "Success",
                    style =
                        BadgeStyle(
                            colors =
                                BadgeColors(
                                    background = SolidColor(Color(0xFF4CAF50)),
                                    backgroundDisabled = SolidColor(Color(0xFFE0E0E0)),
                                    backgroundFocused = SolidColor(Color(0xFF45A049)),
                                    backgroundPressed = SolidColor(Color(0xFF3D8B40)),
                                    backgroundHovered = SolidColor(Color(0xFF45A049)),
                                    content = Color.White,
                                    contentDisabled = Color.Gray,
                                    contentFocused = Color.White,
                                    contentPressed = Color.White,
                                    contentHovered = Color.White,
                                ),
                            metrics = JewelTheme.badgeStyle.metrics,
                        ),
                )

                Badge(
                    text = "Warning",
                    style =
                        BadgeStyle(
                            colors =
                                BadgeColors(
                                    background = SolidColor(Color(0xFFFF9800)),
                                    backgroundDisabled = SolidColor(Color(0xFFE0E0E0)),
                                    backgroundFocused = SolidColor(Color(0xFFFB8C00)),
                                    backgroundPressed = SolidColor(Color(0xFFEF6C00)),
                                    backgroundHovered = SolidColor(Color(0xFFFB8C00)),
                                    content = Color.White,
                                    contentDisabled = Color.Gray,
                                    contentFocused = Color.White,
                                    contentPressed = Color.White,
                                    contentHovered = Color.White,
                                ),
                            metrics = JewelTheme.badgeStyle.metrics,
                        ),
                )

                Badge(
                    text = "Error",
                    style =
                        BadgeStyle(
                            colors =
                                BadgeColors(
                                    background = SolidColor(Color(0xFFF44336)),
                                    backgroundDisabled = SolidColor(Color(0xFFE0E0E0)),
                                    backgroundFocused = SolidColor(Color(0xFFE53935)),
                                    backgroundPressed = SolidColor(Color(0xFFD32F2F)),
                                    backgroundHovered = SolidColor(Color(0xFFE53935)),
                                    content = Color.White,
                                    contentDisabled = Color.Gray,
                                    contentFocused = Color.White,
                                    contentPressed = Color.White,
                                    contentHovered = Color.White,
                                ),
                            metrics = JewelTheme.badgeStyle.metrics,
                        ),
                )

                Badge(
                    text = "Horizontal Gradient",
                    style =
                        BadgeStyle(
                            colors =
                                BadgeColors(
                                    background =
                                        Brush.horizontalGradient(colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
                                    backgroundDisabled = SolidColor(Color.Gray),
                                    backgroundFocused =
                                        Brush.horizontalGradient(colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
                                    backgroundPressed =
                                        Brush.horizontalGradient(colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
                                    backgroundHovered =
                                        Brush.horizontalGradient(colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))),
                                    content = Color.White,
                                    contentDisabled = Color.Gray,
                                    contentFocused = Color.White,
                                    contentPressed = Color.White,
                                    contentHovered = Color.White,
                                ),
                            metrics = JewelTheme.badgeStyle.metrics,
                        ),
                )
            }

            GroupHeader("Rounded Corners")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(
                    text = "2dp",
                    style =
                        BadgeStyle(
                            colors = JewelTheme.badgeStyle.colors,
                            metrics =
                                BadgeMetrics(
                                    cornerSize = CornerSize(2.dp),
                                    padding = JewelTheme.badgeStyle.metrics.padding,
                                    minSize = JewelTheme.badgeStyle.metrics.minSize,
                                ),
                        ),
                )

                Badge(
                    text = "4dp",
                    style =
                        BadgeStyle(
                            colors = JewelTheme.badgeStyle.colors,
                            metrics =
                                BadgeMetrics(
                                    cornerSize = CornerSize(4.dp),
                                    padding = JewelTheme.badgeStyle.metrics.padding,
                                    minSize = JewelTheme.badgeStyle.metrics.minSize,
                                ),
                        ),
                )

                Badge(
                    text = "8dp",
                    style =
                        BadgeStyle(
                            colors = JewelTheme.badgeStyle.colors,
                            metrics =
                                BadgeMetrics(
                                    cornerSize = CornerSize(8.dp),
                                    padding = JewelTheme.badgeStyle.metrics.padding,
                                    minSize = JewelTheme.badgeStyle.metrics.minSize,
                                ),
                        ),
                )

                Badge(
                    text = "Pill",
                    style =
                        BadgeStyle(
                            colors = JewelTheme.badgeStyle.colors,
                            metrics =
                                BadgeMetrics(
                                    cornerSize = CornerSize(100),
                                    padding = JewelTheme.badgeStyle.metrics.padding,
                                    minSize = JewelTheme.badgeStyle.metrics.minSize,
                                ),
                        ),
                )
            }
        }
    }
}
