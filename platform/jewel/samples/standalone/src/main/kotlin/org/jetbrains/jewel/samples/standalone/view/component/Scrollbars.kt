package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.styling.defaults
import org.jetbrains.jewel.intui.standalone.styling.macOsDark
import org.jetbrains.jewel.intui.standalone.styling.macOsLight
import org.jetbrains.jewel.intui.standalone.styling.winOsDark
import org.jetbrains.jewel.intui.standalone.styling.winOsLight
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import org.jetbrains.skiko.hostOs
import java.util.Locale

@Composable
@View(title = "Scrollbars", position = 14, icon = "icons/components/scrollbar.svg")
fun Scrollbars() {
    Column {
        val isDark = JewelTheme.isDark
        var alwaysVisible by remember { mutableStateOf(false) }
        val initialStyle by remember { mutableStateOf(readStyle(hostOs.isMacOS, isDark)) }
        var style by remember { mutableStateOf(initialStyle) }

        LaunchedEffect(alwaysVisible) {
            style =
                if (alwaysVisible) {
                    ScrollbarStyle(
                        colors = style.colors,
                        metrics = style.metrics,
                        trackClickBehavior = style.trackClickBehavior,
                        scrollbarVisibility = ScrollbarVisibility.AlwaysVisible,
                    )
                } else {
                    ScrollbarStyle(
                        colors = style.colors,
                        metrics = style.metrics,
                        trackClickBehavior = style.trackClickBehavior,
                        scrollbarVisibility = ScrollbarVisibility.WhenScrolling.defaults(),
                    )
                }
        }

        CheckboxRow(
            checked = alwaysVisible,
            onCheckedChange = { alwaysVisible = it },
            text = "Always visible",
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            Modifier.padding(horizontal = 16.dp).height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("LazyColumn", fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))

                Box(Modifier.border(1.dp, JewelTheme.globalColors.borders.normal)) {
                    val scrollState = rememberLazyListState()
                    LazyColumn(
                        Modifier
                            .width(200.dp)
                            .padding(end = JewelTheme.scrollbarStyle.metrics.thumbThicknessExpanded)
                            .align(Alignment.CenterStart),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        state = scrollState,
                    ) {
                        items(LIST_ITEMS) { item ->
                            Column {
                                Text(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    text = item,
                                )
                                Divider(orientation = Orientation.Horizontal, color = Color.Gray)
                            }
                        }
                    }
                    VerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        style = style,
                    )
                }
            }
            Column {
                Text("Column", fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))

                Box(Modifier.border(1.dp, JewelTheme.globalColors.borders.normal)) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier =
                            Modifier
                                .verticalScroll(scrollState)
                                .padding(end = JewelTheme.scrollbarStyle.metrics.thumbThicknessExpanded)
                                .align(Alignment.CenterStart),
                    ) {
                        LIST_ITEMS.forEach {
                            Text(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                text = it,
                            )
                        }
                    }
                    VerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier.align(Alignment.CenterEnd),
                        style = style,
                    )
                }
            }
        }
    }
}

fun readStyle(
    isMac: Boolean,
    isDark: Boolean,
): ScrollbarStyle =
    if (isDark) {
        if (isMac) {
            ScrollbarStyle.macOsDark()
        } else {
            ScrollbarStyle.winOsDark()
        }
    } else {
        if (isMac) {
            ScrollbarStyle.macOsLight()
        } else {
            ScrollbarStyle.winOsLight()
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
    LOREM_IPSUM
        .split(",")
        .map { lorem ->
            lorem
                .trim()
                .replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                }
        }
