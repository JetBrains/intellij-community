package org.jetbrains.jewel.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.ContentAlpha.disabled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.ButtonState
import org.jetbrains.jewel.TabState

@Stable
interface TabStyle {

    val colors: TabColors
    val metrics: TabMetrics
    val icons: TabIcons
    val contentAlpha: TabContentAlpha
}

@Immutable
interface TabIcons {

    val close: StatefulPainterProvider<ButtonState>
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
            else -> state.chooseValue(
                normal = content,
                disabled = contentDisabled,
                focused = contentFocused,
                pressed = contentPressed,
                hovered = contentHovered,
                active = content
            )
        }
    )

    @Composable
    fun backgroundFor(state: TabState) = rememberUpdatedState(
        when {
            state.isSelected -> backgroundSelected
            else -> state.chooseValue(
                normal = background,
                disabled = backgroundDisabled,
                focused = backgroundFocused,
                pressed = backgroundPressed,
                hovered = backgroundHovered,
                active = background
            )
        }
    )

    @Composable
    fun underlineFor(state: TabState) = rememberUpdatedState(
        when {
            state.isSelected -> underlineSelected
            else -> state.chooseValue(
                normal = underline,
                disabled = underlineDisabled,
                focused = underlineFocused,
                pressed = underlinePressed,
                hovered = underlineHovered,
                active = underline
            )
        }
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
            else -> state.chooseValue(
                normal = iconNormal,
                disabled = iconDisabled,
                focused = iconFocused,
                pressed = iconPressed,
                hovered = iconHovered,
                active = iconNormal
            )
        }
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
            else -> state.chooseValue(
                normal = labelNormal,
                disabled = labelDisabled,
                focused = labelFocused,
                pressed = labelPressed,
                hovered = labelHovered,
                active = labelNormal
            )
        }
    )
}

val LocalDefaultTabStyle = staticCompositionLocalOf<TabStyle> {
    error("No LocalTabStyle provided")
}

val LocalEditorTabStyle = staticCompositionLocalOf<TabStyle> {
    error("No LocalTabStyle provided")
}
