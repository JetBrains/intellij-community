package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.theme.scrollbarStyle
import kotlin.time.DurationUnit
import androidx.compose.foundation.ScrollbarStyle as ComposeScrollbarStyle

@Composable
public fun VerticalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
) {
    val shape by remember { mutableStateOf(RoundedCornerShape(style.metrics.thumbCornerSize)) }
    val hoverDurationMillis by remember { mutableStateOf(style.hoverDuration.inWholeMilliseconds) }

    val composeScrollbarStyle = ComposeScrollbarStyle(
        minimalHeight = style.metrics.minThumbLength,
        thickness = style.metrics.thumbThickness,
        shape = shape,
        hoverDurationMillis = hoverDurationMillis.toInt(),
        unhoverColor = style.colors.thumbBackground,
        hoverColor = style.colors.thumbBackgroundHovered,
    )

    CompositionLocalProvider(LocalScrollbarStyle provides composeScrollbarStyle) {
        VerticalScrollbar(
            adapter = adapter,
            modifier = modifier.padding(style.metrics.trackPadding),
            reverseLayout = reverseLayout,
            style = LocalScrollbarStyle.current,
            interactionSource = interactionSource,
        )
    }
}

@Composable
public fun HorizontalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
) {
    val shape by remember { mutableStateOf(RoundedCornerShape(style.metrics.thumbCornerSize)) }
    val hoverDurationMillis by remember {
        mutableStateOf(style.hoverDuration.toInt(DurationUnit.MILLISECONDS))
    }

    val composeScrollbarStyle = ComposeScrollbarStyle(
        minimalHeight = style.metrics.minThumbLength,
        thickness = style.metrics.thumbThickness,
        shape = shape,
        hoverDurationMillis = hoverDurationMillis,
        unhoverColor = style.colors.thumbBackground,
        hoverColor = style.colors.thumbBackgroundHovered,
    )
    CompositionLocalProvider(LocalScrollbarStyle provides composeScrollbarStyle) {
        HorizontalScrollbar(
            adapter = adapter,
            modifier = modifier.padding(style.metrics.trackPadding),
            reverseLayout = reverseLayout,
            style = LocalScrollbarStyle.current,
            interactionSource = interactionSource,
        )
    }
}

@Composable
public fun TabStripHorizontalScrollbar(
    adapter: ScrollbarAdapter,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: ScrollbarStyle = JewelTheme.scrollbarStyle,
) {
    val shape by remember { mutableStateOf(RoundedCornerShape(style.metrics.thumbCornerSize)) }
    val hoverDurationMillis by remember {
        mutableStateOf(style.hoverDuration.inWholeMilliseconds.toInt())
    }

    val composeScrollbarStyle = ComposeScrollbarStyle(
        minimalHeight = style.metrics.minThumbLength,
        thickness = 3.dp,
        shape = shape,
        hoverDurationMillis = hoverDurationMillis,
        unhoverColor = style.colors.thumbBackground,
        hoverColor = style.colors.thumbBackgroundHovered,
    )
    CompositionLocalProvider(LocalScrollbarStyle provides composeScrollbarStyle) {
        HorizontalScrollbar(
            adapter = adapter,
            modifier = modifier.padding(1.dp),
            reverseLayout = reverseLayout,
            style = LocalScrollbarStyle.current,
            interactionSource = interactionSource,
        )
    }
}
