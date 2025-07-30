// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
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
    modifier: Modifier = Modifier,
) {
    val baseStyle = JewelTheme.scrollbarStyle
    var alwaysVisible by remember { mutableStateOf(hostOs != OS.MacOS) }
    var reducedContent by remember { mutableStateOf(false) }
    var clickBehavior by remember { mutableStateOf(baseStyle.trackClickBehavior) }

    val scrollbarStyle by
        rememberScrollbarStyle(
            alwaysVisible,
            clickBehavior,
            baseStyle,
            alwaysVisibleScrollbarVisibility,
            whenScrollingScrollbarVisibility,
        )

    VerticallyScrollableContainer(modifier = modifier.fillMaxSize(), style = scrollbarStyle) {
        Column(
            modifier = Modifier.padding(bottom = 2.dp).padding(end = scrollbarContentSafePadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsRow(
                alwaysVisible,
                reducedContent,
                clickBehavior,
                onAlwaysVisibleChange = { alwaysVisible = it },
                onReducedContentChange = { reducedContent = it },
                onClickBehaviorChange = { clickBehavior = it },
            )

            Row(
                Modifier.fillMaxWidth().height(250.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val items by remember { derivedStateOf { if (reducedContent) LIST_ITEMS.take(3) else LIST_ITEMS } }

                LazyColumnWithScrollbar(items, scrollbarStyle, Modifier.weight(1f).fillMaxHeight())
                ColumnWithScrollbar(items, scrollbarStyle, Modifier.weight(1f).fillMaxHeight())
                AlignedContentExample(items, scrollbarStyle, Modifier.weight(1f).fillMaxHeight())
            }

            val ipsum by remember {
                derivedStateOf { if (reducedContent) OneLineIpsum.take(64).substringBeforeLast(' ') else OneLineIpsum }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RowWithScrollbar(ipsum, scrollbarStyle, Modifier.fillMaxWidth())
                LazyRowWithScrollbar(ipsum, scrollbarStyle, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun rememberScrollbarStyle(
    alwaysVisible: Boolean,
    clickBehavior: TrackClickBehavior,
    baseStyle: ScrollbarStyle,
    alwaysVisibleScrollbarVisibility: ScrollbarVisibility,
    whenScrollingScrollbarVisibility: ScrollbarVisibility,
): MutableState<ScrollbarStyle> =
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

@Composable
private fun SettingsRow(
    alwaysVisible: Boolean,
    reducedContent: Boolean,
    clickBehavior: TrackClickBehavior,
    onAlwaysVisibleChange: (Boolean) -> Unit,
    onReducedContentChange: (Boolean) -> Unit,
    onClickBehaviorChange: (TrackClickBehavior) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CheckboxRow(checked = alwaysVisible, onCheckedChange = onAlwaysVisibleChange, text = "Always visible")

        Spacer(Modifier.width(16.dp))

        CheckboxRow(checked = reducedContent, onCheckedChange = onReducedContentChange, text = "Reduced content")

        Spacer(Modifier.width(16.dp))
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
private fun LazyColumnWithScrollbar(items: List<String>, style: ScrollbarStyle, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("LazyColumn", style = JewelTheme.typography.h2TextStyle)

        Spacer(Modifier.height(8.dp))

        val scrollState = rememberLazyListState()
        VerticallyScrollableContainer(
            scrollState,
            modifier =
                Modifier.fillMaxSize()
                    .background(JewelTheme.textAreaStyle.colors.background)
                    .border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal),
            style = style,
        ) {
            LazyColumn(state = scrollState, modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(items) { index, item ->
                    Column {
                        Text(
                            modifier =
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    .padding(end = scrollbarContentSafePadding(style)),
                            text = item,
                        )

                        if (index != items.lastIndex) {
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
private fun ColumnWithScrollbar(items: List<String>, style: ScrollbarStyle, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Column", fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))

        VerticallyScrollableContainer(
            style = style,
            modifier =
                Modifier.fillMaxSize()
                    .background(JewelTheme.textAreaStyle.colors.background)
                    .border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                for ((index, line) in items.withIndex()) {
                    Text(
                        modifier =
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                .padding(end = scrollbarContentSafePadding(style)),
                        text = line,
                    )
                    if (index < items.lastIndex) {
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
private fun AlignedContentExample(items: List<String>, scrollbarStyle: ScrollbarStyle, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Column (aligned)", fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))

        VerticallyScrollableContainer(
            style = scrollbarStyle,
            modifier =
                Modifier.fillMaxSize().border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal),
        ) {
            val shape = RoundedCornerShape(4.dp)
            val borderColor = getBorderColor()
            val backgroundColor = getBackgroundColor()
            Column(
                modifier =
                    Modifier.align(Alignment.Center)
                        .background(color = backgroundColor, shape)
                        .border(1.dp, borderColor, shape)
                        .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val words = remember(items) { items.map { it.substringBefore(" ") } }
                for (word in words) {
                    Text(word)
                }
            }
        }
    }
}

@Composable
private fun RowWithScrollbar(content: String, scrollbarStyle: ScrollbarStyle, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("Row", fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))

        HorizontallyScrollableContainer(
            modifier =
                Modifier.fillMaxWidth().border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal),
            style = scrollbarStyle,
        ) {
            Text(
                content,
                modifier =
                    Modifier.background(JewelTheme.textAreaStyle.colors.background)
                        .padding(bottom = scrollbarContentSafePadding(scrollbarStyle))
                        .padding(8.dp),
            )
        }
    }
}

@Composable
private fun LazyRowWithScrollbar(content: String, scrollbarStyle: ScrollbarStyle, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("LazyRow", fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))

        val scrollState = rememberLazyListState()
        HorizontallyScrollableContainer(
            scrollState = scrollState,
            modifier =
                Modifier.fillMaxWidth().border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal),
            style = scrollbarStyle,
        ) {
            val words = remember(content) { content.split(' ').filter { it.isNotBlank() } }
            val shape = RoundedCornerShape(4.dp)
            val borderColor = getBorderColor()
            val backgroundColor = getBackgroundColor()

            LazyRow(
                state = scrollState,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = scrollbarContentSafePadding(scrollbarStyle)),
            ) {
                items(words) { word ->
                    Text(
                        word,
                        Modifier.background(backgroundColor, shape)
                            .border(1.dp, borderColor)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun getBorderColor(): Color =
    if (JewelTheme.isDark) {
        JewelTheme.colorPalette.blueOrNull(2) ?: Color(0xFF2E436E)
    } else {
        JewelTheme.colorPalette.blueOrNull(2) ?: Color(0xFF315FBD)
    }

@Composable
private fun getBackgroundColor(): Color =
    if (JewelTheme.isDark) {
        JewelTheme.colorPalette.grayOrNull(1) ?: Color(0xFF1E1F22)
    } else {
        JewelTheme.colorPalette.grayOrNull(14) ?: Color(0xFFFFFFFF)
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

private val OneLineIpsum = LOREM_IPSUM.replace('\n', ' ')

private val LIST_ITEMS =
    LOREM_IPSUM.split(",")
        .map { lorem ->
            lorem.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        .let { it + it + it + it + it + it }
