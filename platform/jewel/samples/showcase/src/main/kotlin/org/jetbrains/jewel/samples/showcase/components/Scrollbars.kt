// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontallyScrollableContainer
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.jewel.ui.theme.textAreaStyle
import org.jetbrains.jewel.ui.typography
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

@Composable
public fun Scrollbars(
    alwaysVisibleScrollbarVisibility: ScrollbarVisibility,
    whenScrollingScrollbarVisibility: ScrollbarVisibility,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val baseStyle = JewelTheme.scrollbarStyle
        var alwaysVisible by remember { mutableStateOf(hostOs != OS.MacOS) }
        var clickBehavior by remember { mutableStateOf(baseStyle.trackClickBehavior) }
        SettingsRow(alwaysVisible, clickBehavior, { alwaysVisible = it }, { clickBehavior = it })

        val style by
            remember(alwaysVisible, clickBehavior, baseStyle) {
                mutableStateOf(
                    if (alwaysVisible) {
                        ScrollbarStyle(
                            colors = baseStyle.colors,
                            metrics = baseStyle.metrics,
                            trackClickBehavior = clickBehavior,
                            scrollbarVisibility = alwaysVisibleScrollbarVisibility,
                        )
                    } else {
                        ScrollbarStyle(
                            colors = baseStyle.colors,
                            metrics = baseStyle.metrics,
                            trackClickBehavior = clickBehavior,
                            scrollbarVisibility = whenScrollingScrollbarVisibility,
                        )
                    }
                )
            }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyColumnWithScrollbar(style, Modifier.height(200.dp).weight(1f))
            ColumnWithScrollbar(style, Modifier.height(200.dp).weight(1f))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalScrollbarContent(style, Modifier.weight(1f).fillMaxHeight())
            AlignedContentExample(style, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun SettingsRow(
    alwaysVisible: Boolean,
    clickBehavior: TrackClickBehavior,
    onAlwaysVisibleChange: (Boolean) -> Unit,
    onClickBehaviorChange: (TrackClickBehavior) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckboxRow(checked = alwaysVisible, onCheckedChange = onAlwaysVisibleChange, text = "Always visible")

        Spacer(Modifier.weight(1f))

        Text("Track click behavior:")

        Spacer(Modifier.width(8.dp))

        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RadioButtonRow(
                text = "Jump to spot",
                selected = clickBehavior == TrackClickBehavior.JumpToSpot,
                onClick = { onClickBehaviorChange(TrackClickBehavior.JumpToSpot) },
            )
            RadioButtonRow(
                text = "Move by one page",
                selected = clickBehavior == TrackClickBehavior.NextPage,
                onClick = { onClickBehaviorChange(TrackClickBehavior.NextPage) },
            )
        }
    }
}

@Composable
private fun LazyColumnWithScrollbar(style: ScrollbarStyle, modifier: Modifier) {
    Column(modifier) {
        Text("LazyColumn", style = JewelTheme.typography.h2TextStyle)

        Spacer(Modifier.height(8.dp))

        val scrollState = rememberLazyListState()
        VerticallyScrollableContainer(
            scrollState,
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal),
            style = style,
        ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize().background(JewelTheme.textAreaStyle.colors.background),
            ) {
                itemsIndexed(LIST_ITEMS) { index, item ->
                    Column {
                        Text(
                            modifier =
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    .padding(end = scrollbarContentSafePadding(style)),
                            text = item,
                        )

                        if (index != LIST_ITEMS.lastIndex) {
                            Divider(
                                orientation = Orientation.Horizontal,
                                modifier = Modifier.fillMaxWidth(),
                                color = JewelTheme.globalColors.borders.normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnWithScrollbar(style: ScrollbarStyle, modifier: Modifier) {
    Column(modifier) {
        Text("Column", fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))

        VerticallyScrollableContainer(
            modifier = Modifier.border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal),
            style = style,
        ) {
            Column(
                modifier = Modifier.background(JewelTheme.textAreaStyle.colors.background).align(Alignment.CenterStart)
            ) {
                LIST_ITEMS.forEachIndexed { index, line ->
                    Text(
                        modifier =
                            Modifier.padding(horizontal = 8.dp).padding(end = scrollbarContentSafePadding(style)),
                        text = line,
                    )
                    if (index < LIST_ITEMS.lastIndex) {
                        Box(Modifier.height(8.dp), contentAlignment = Alignment.CenterStart) {
                            Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalScrollbarContent(scrollbarStyle: ScrollbarStyle, modifier: Modifier) {
    Column(modifier) {
        Text("Column", fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))

        HorizontallyScrollableContainer(
            modifier =
                Modifier.fillMaxSize().border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal),
            style = scrollbarStyle,
        ) {
            Column(
                modifier =
                    Modifier.fillMaxHeight()
                        .background(JewelTheme.textAreaStyle.colors.background)
                        .padding(bottom = scrollbarContentSafePadding(scrollbarStyle))
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val oneLineIpsum = LOREM_IPSUM.replace('\n', ' ')
                repeat(4) { Text(oneLineIpsum) }
            }
        }
    }
}

@Composable
private fun AlignedContentExample(scrollbarStyle: ScrollbarStyle, modifier: Modifier) {
    Column(modifier) {
        Text("Column", fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))

        VerticallyScrollableContainer(
            style = scrollbarStyle,
            modifier =
                Modifier.fillMaxWidth().border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal),
        ) {
            val shape = RoundedCornerShape(4.dp)
            val borderColor =
                if (JewelTheme.isDark) {
                    JewelTheme.colorPalette.blueOrNull(2) ?: Color(0xFF2E436E)
                } else {
                    JewelTheme.colorPalette.blueOrNull(2) ?: Color(0xFF315FBD)
                }
            val backgroundColor =
                if (JewelTheme.isDark) {
                    JewelTheme.colorPalette.grayOrNull(1) ?: Color(0xFF1E1F22)
                } else {
                    JewelTheme.colorPalette.grayOrNull(14) ?: Color(0xFFFFFFFF)
                }
            Column(
                modifier =
                    Modifier.align(Alignment.Center)
                        .background(color = backgroundColor, shape)
                        .border(1.dp, borderColor, shape)
                        .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("First Row")
                Text("Second Row")
                Text("Third Row")
                Text("Fourth Row")
                Text("Fifth Row")
                Text("Sixth Row")
                Text("Seventh Row")
            }
        }
    }
}

@Suppress("SpellCheckingInspection")
private const val LOREM_IPSUM =
    "Lorem ipsum dolor sit amet, consectetur adipiscing elit. \n" +
        "Sed auctor, neque in accumsan vehicula, enim purus vestibulum odio, non tristique dolor quam vel ipsum. \n" +
        "Proin egestas, orci id hendrerit bibendum, nisl neque imperdiet nisl, a euismod nibh diam nec lectus. \n" +
        "Duis euismod, quam nec aliquam iaculis, dolor lorem bibendum turpis, vel malesuada augue sapien vel mi. \n" +
        "Quisque ut facilisis nibh. Maecenas euismod hendrerit sem, ac scelerisque odio auctor nec. \n" +
        "Sed sit amet consequat eros. Donec nisl tellus, accumsan nec ligula in, eleifend sodales sem. \n" +
        "Sed malesuada, nulla ac eleifend fermentum, nibh mi consequat quam, quis convallis lacus nunc eu dui. \n" +
        "Pellentesque eget enim quis orci porttitor consequat sed sed quam. \n" +
        "Sed aliquam, nisl et lacinia lacinia, diam nunc laoreet nisi, sit amet consectetur dolor lorem et sem. \n" +
        "Duis ultricies, mauris in aliquam interdum, orci nulla finibus massa, a tristique urna sapien vel quam. \n" +
        "Sed nec sapien nec dui rhoncus bibendum. Sed blandit bibendum libero."

private val LIST_ITEMS =
    LOREM_IPSUM.split(",")
        .map { lorem ->
            lorem.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        .let { it + it + it + it + it + it }
