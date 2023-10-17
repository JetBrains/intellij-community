package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.TabState
import org.jetbrains.jewel.painter.PainterProvider

@Stable
interface TabStyle {

    val colors: TabColors
    val metrics: TabMetrics
    val icons: TabIcons
    val contentAlpha: TabContentAlpha
}

@Immutable
interface TabIcons {

    val close: PainterProvider
}

@Stable
interface TabMetrics {

    val underlineThickness: Dp
    val tabPadding: PaddingValues
    val tabHeight: Dp
    val closeContentGap: Dp
}

@Immutable
interface TabColors {

    val background: Color
    val backgroundDisabled: Color
    val backgroundFocused: Color
    val backgroundPressed: Color
    val backgroundHovered: Color
    val backgroundSelected: Color

    val content: Color
    val contentDisabled: Color
    val contentFocused: Color
    val contentPressed: Color
    val contentHovered: Color
    val contentSelected: Color

    val underline: Color
    val underlineDisabled: Color
    val underlineFocused: Color
    val underlinePressed: Color
    val underlineHovered: Color
    val underlineSelected: Color

    @Composable
    fun contentFor(state: TabState) = rememberUpdatedState(
        when {
            state.isSelected -> contentSelected
            else -> state.chooseValueIgnoreCompat(
                normal = content,
                disabled = contentDisabled,
                focused = contentFocused,
                pressed = contentPressed,
                hovered = contentHovered,
                active = content,
            )
        },
    )

    @Composable
    fun backgroundFor(state: TabState) = rememberUpdatedState(
        when {
            !state.isEnabled -> backgroundDisabled
            state.isPressed -> backgroundPressed
            state.isHovered -> backgroundHovered
            state.isFocused -> backgroundFocused
            state.isActive -> background
            state.isSelected -> backgroundSelected
            else -> background
        },
    )

    @Composable
    fun underlineFor(state: TabState) = rememberUpdatedState(
        when {
            state.isSelected -> underlineSelected
            else -> state.chooseValueIgnoreCompat(
                normal = underline,
                disabled = underlineDisabled,
                focused = underlineFocused,
                pressed = underlinePressed,
                hovered = underlineHovered,
                active = underline,
            )
        },
    )
}

@Immutable
interface TabContentAlpha {

    val iconNormal: Float
    val iconDisabled: Float
    val iconFocused: Float
    val iconPressed: Float
    val iconHovered: Float
    val iconSelected: Float

    @Composable
    fun iconFor(state: TabState) = rememberUpdatedState(
        when {
            state.isSelected -> iconSelected
            else -> state.chooseValueIgnoreCompat(
                normal = iconNormal,
                disabled = iconDisabled,
                focused = iconFocused,
                pressed = iconPressed,
                hovered = iconHovered,
                active = iconNormal,
            )
        },
    )

    val labelNormal: Float
    val labelDisabled: Float
    val labelFocused: Float
    val labelPressed: Float
    val labelHovered: Float
    val labelSelected: Float

    @Composable
    fun labelFor(state: TabState) = rememberUpdatedState(
        when {
            state.isSelected -> labelSelected
            else -> state.chooseValueIgnoreCompat(
                normal = labelNormal,
                disabled = labelDisabled,
                focused = labelFocused,
                pressed = labelPressed,
                hovered = labelHovered,
                active = labelNormal,
            )
        },
    )
}

// Tabs are the only components that handle hover states
@Composable
private fun <T> TabState.chooseValueIgnoreCompat(
    normal: T,
    disabled: T,
    focused: T,
    pressed: T,
    hovered: T,
    active: T,
): T =
    when {
        !isEnabled -> disabled
        isPressed -> pressed
        isHovered -> hovered
        isFocused -> focused
        isActive -> active
        else -> normal
    }

val LocalDefaultTabStyle = staticCompositionLocalOf<TabStyle> {
    error("No LocalTabStyle provided")
}

val LocalEditorTabStyle = staticCompositionLocalOf<TabStyle> {
    error("No LocalTabStyle provided")
}
