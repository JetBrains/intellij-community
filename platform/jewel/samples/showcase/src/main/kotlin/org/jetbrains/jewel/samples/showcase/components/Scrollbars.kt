// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.showcase.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollableState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.modifier.border
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.samples.showcase.ShowcaseIcons
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontallyScrollableContainer
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.RadioButtonChip
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.theme.colorPalette
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.jewel.ui.theme.textAreaStyle
import org.jetbrains.jewel.ui.typography
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs

/** Showcases the Scrollbar component. */
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
    var scrollbarConfiguration by remember { mutableStateOf(ScrollbarConfiguration.MANUAL) }

    val scrollbarStyle by
        rememberScrollbarStyle(
            alwaysVisible,
            clickBehavior,
            baseStyle,
            alwaysVisibleScrollbarVisibility,
            whenScrollingScrollbarVisibility,
            scrollbarConfiguration,
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
                scrollbarConfiguration,
                onAlwaysVisibleChange = { alwaysVisible = it },
                onReducedContentChange = { reducedContent = it },
                onClickBehaviorChange = { clickBehavior = it },
                onConfigurationChange = { scrollbarConfiguration = it },
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
            RowWithScrollbar(ipsum, scrollbarStyle, Modifier.fillMaxWidth())
            LazyRowWithScrollbar(ipsum, scrollbarStyle, Modifier.fillMaxWidth())

            Row(
                Modifier.fillMaxWidth().height(250.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpannedGridWithScrollbar(
                    title = "LazyVerticalGrid (default adapter)",
                    spanAware = false,
                    style = scrollbarStyle,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                SpannedGridWithScrollbar(
                    title = "LazyVerticalGrid (span-aware adapter)",
                    spanAware = true,
                    style = scrollbarStyle,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
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
    scrollbarConfiguration: ScrollbarConfiguration,
): MutableState<ScrollbarStyle> =
    remember(alwaysVisible, clickBehavior, baseStyle, scrollbarConfiguration) {
        mutableStateOf(
            if (scrollbarConfiguration == ScrollbarConfiguration.SYSTEM) {
                baseStyle
            } else if (alwaysVisible) {
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
    scrollbarConfiguration: ScrollbarConfiguration,
    onAlwaysVisibleChange: (Boolean) -> Unit,
    onReducedContentChange: (Boolean) -> Unit,
    onClickBehaviorChange: (TrackClickBehavior) -> Unit,
    onConfigurationChange: (ScrollbarConfiguration) -> Unit,
) {
    Column {
        Text("Configuration", style = JewelTheme.typography.h2TextStyle)

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScrollbarConfiguration.entries.forEach { config ->
                RadioButtonChip(
                    selected = scrollbarConfiguration == config,
                    onClick = { onConfigurationChange(config) },
                    enabled = true,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(config.icon, config.label, hint = Selected(scrollbarConfiguration == config))
                        Text(config.label)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = scrollbarConfiguration == ScrollbarConfiguration.MANUAL,
            enter =
                expandVertically(expandFrom = Alignment.Top) +
                    scaleIn(transformOrigin = TransformOrigin(0f, 0f)) +
                    fadeIn(initialAlpha = 0.3f),
            exit =
                shrinkVertically(shrinkTowards = Alignment.Top) +
                    scaleOut(transformOrigin = TransformOrigin(0f, 0f)) +
                    fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckboxRow(
                        checked = alwaysVisible,
                        onCheckedChange = onAlwaysVisibleChange,
                        text = "Always visible",
                    )

                    Spacer(Modifier.width(16.dp))

                    CheckboxRow(
                        checked = reducedContent,
                        onCheckedChange = onReducedContentChange,
                        text = "Reduced content",
                    )

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
        }

        Spacer(Modifier.height(16.dp))
        Divider(Orientation.Horizontal, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LazyColumnWithScrollbar(items: List<String>, style: ScrollbarStyle, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text("LazyColumn", style = JewelTheme.typography.h2TextStyle)

        Spacer(Modifier.height(8.dp))

        val scrollState = rememberLazyListState()
        VerticallyScrollableContainer(
            scrollState as ScrollableState,
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

/**
 * The default scrollbar adapter assumes every item occupies exactly one slot, so it underestimates the number of lines
 * in the grid, and the thumb overshoots the track while scrolling. Passing a custom [ScrollbarAdapter] that knows the
 * actual item structure fixes it.
 */
@Composable
private fun SpannedGridWithScrollbar(
    title: String,
    spanAware: Boolean,
    style: ScrollbarStyle,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))

        val gridState = rememberLazyGridState()
        val adapter =
            if (spanAware) {
                remember(gridState) { SpanAwareGridScrollbarAdapter(gridState, GRID_COLUMNS, GRID_HEADER_INTERVAL) }
            } else {
                null
            }

        VerticallyScrollableContainer(
            scrollState = gridState,
            style = style,
            adapter = adapter,
            modifier =
                Modifier.fillMaxSize()
                    .background(JewelTheme.textAreaStyle.colors.background)
                    .border(Stroke.Alignment.Outside, 1.dp, JewelTheme.globalColors.borders.normal),
        ) {
            val shape = RoundedCornerShape(4.dp)
            val borderColor = getBorderColor()
            val backgroundColor = getBackgroundColor()

            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp + scrollbarContentSafePadding(style)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    count = GRID_ITEM_COUNT,
                    span = { index ->
                        if (index % GRID_HEADER_INTERVAL == 0) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                    },
                ) { index ->
                    if (index % GRID_HEADER_INTERVAL == 0) {
                        Box(
                            modifier =
                                Modifier.height(GRID_CELL_HEIGHT)
                                    .fillMaxWidth()
                                    .background(backgroundColor, shape)
                                    .border(1.dp, borderColor, shape)
                                    .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text("Group ${index / GRID_HEADER_INTERVAL + 1}")
                        }
                    } else {
                        Box(
                            modifier = Modifier.height(GRID_CELL_HEIGHT).background(backgroundColor, shape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$index")
                        }
                    }
                }
            }
        }
    }
}

/**
 * A [ScrollbarAdapter] for a [LazyVerticalGrid] whose items have heterogeneous spans.
 *
 * The default adapter estimates line counts assuming one slot per item, which is wrong when some items span the full
 * line. This is a limitation CMP cannot fix generically, as it has no information about non-composed lines. This
 * adapter computes exact values by encoding the known structure of the grid: every [headerInterval]-th item is a
 * full-span header, all other items span one of the [columns] slots, and all lines are equally tall.
 */
private class SpanAwareGridScrollbarAdapter(
    private val gridState: LazyGridState,
    private val columns: Int,
    private val headerInterval: Int,
) : ScrollbarAdapter {
    // Each group of headerInterval items renders as one header line plus the following
    // headerInterval - 1 single-span items, packed columns per line (last line ragged)
    private val linesPerGroup = 1 + (headerInterval - 1 + columns - 1) / columns

    private val layoutInfo
        get() = gridState.layoutInfo

    // All cells are equally tall, so any visible item measures the line height
    private val lineHeight
        get() = layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height?.toDouble() ?: 0.0

    private val linePitch
        get() = lineHeight + layoutInfo.mainAxisItemSpacing

    private val totalLines
        get() = if (layoutInfo.totalItemsCount == 0) 0 else lineOfIndex(layoutInfo.totalItemsCount - 1) + 1

    private fun lineOfIndex(index: Int): Int {
        val group = index / headerInterval
        val positionInGroup = index % headerInterval
        val lineInGroup = if (positionInGroup == 0) 0 else 1 + (positionInGroup - 1) / columns
        return group * linesPerGroup + lineInGroup
    }

    private fun firstIndexOfLine(line: Int): Int {
        val group = line / linesPerGroup
        val lineInGroup = line % linesPerGroup
        return if (lineInGroup == 0) {
            group * headerInterval
        } else {
            group * headerInterval + 1 + (lineInGroup - 1) * columns
        }
    }

    override val scrollOffset: Double
        get() = lineOfIndex(gridState.firstVisibleItemIndex) * linePitch + gridState.firstVisibleItemScrollOffset

    override val contentSize: Double
        get() {
            val lines = totalLines
            if (lines == 0) return 0.0
            return lines * lineHeight +
                (lines - 1) * layoutInfo.mainAxisItemSpacing +
                layoutInfo.beforeContentPadding +
                layoutInfo.afterContentPadding
        }

    override val viewportSize: Double
        get() = layoutInfo.viewportSize.height.toDouble()

    override suspend fun scrollTo(scrollOffset: Double) {
        val pitch = linePitch
        if (pitch <= 0.0) return

        val maxOffset = (contentSize - viewportSize).coerceAtLeast(0.0)
        val target = scrollOffset.coerceIn(0.0, maxOffset)
        val line = (target / pitch).toInt().coerceIn(0, (totalLines - 1).coerceAtLeast(0))
        val remainderPx = (target - line * pitch).roundToInt()

        gridState.scrollToItem(firstIndexOfLine(line), remainderPx)
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

private const val GRID_COLUMNS = 4
private const val GRID_HEADER_INTERVAL = 10
private const val GRID_ITEM_COUNT = 100
private val GRID_CELL_HEIGHT = 24.dp

private val LIST_ITEMS =
    LOREM_IPSUM.split(",")
        .map { lorem ->
            lorem.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        .let { it + it + it + it + it + it }

internal enum class ScrollbarConfiguration(val icon: IconKey, val label: String) {
    MANUAL(AllIconsKeys.Actions.Edit, "Manual"),
    SYSTEM(ShowcaseIcons.terminal, "System"),
}
