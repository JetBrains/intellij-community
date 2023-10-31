package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.TabState
import org.jetbrains.jewel.ui.painter.PainterProvider

@Stable
@GenerateDataFunctions
class TabStyle(
    val colors: TabColors,
    val metrics: TabMetrics,
    val icons: TabIcons,
    val contentAlpha: TabContentAlpha,
) {

    companion object
}

@Stable
@GenerateDataFunctions
class TabMetrics(
    val underlineThickness: Dp,
    val tabPadding: PaddingValues,
    val tabHeight: Dp,
    val closeContentGap: Dp,
) {

    companion object
}

@Immutable
@GenerateDataFunctions
class TabIcons(val close: PainterProvider) {

    companion object
}

@Immutable
@GenerateDataFunctions
class TabColors(
    val background: Color,
    val backgroundDisabled: Color,
    val backgroundPressed: Color,
    val backgroundHovered: Color,
    val backgroundSelected: Color,
    val content: Color,
    val contentDisabled: Color,
    val contentPressed: Color,
    val contentHovered: Color,
    val contentSelected: Color,
    val underline: Color,
    val underlineDisabled: Color,
    val underlinePressed: Color,
    val underlineHovered: Color,
    val underlineSelected: Color,
) {

    @Composable
    fun contentFor(state: TabState) = rememberUpdatedState(
        when {
            state.isSelected -> contentSelected
            else -> state.chooseValueIgnoreCompat(
                normal = content,
                disabled = contentDisabled,
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
                pressed = underlinePressed,
                hovered = underlineHovered,
                active = underline,
            )
        },
    )

    companion object
}

@Immutable
@GenerateDataFunctions
class TabContentAlpha(
    val iconNormal: Float,
    val iconDisabled: Float,
    val iconPressed: Float,
    val iconHovered: Float,
    val iconSelected: Float,
    val labelNormal: Float,
    val labelDisabled: Float,
    val labelPressed: Float,
    val labelHovered: Float,
    val labelSelected: Float,
) {

    @Composable
    fun iconFor(state: TabState) = rememberUpdatedState(
        when {
            state.isSelected -> iconSelected
            else -> state.chooseValueIgnoreCompat(
                normal = iconNormal,
                disabled = iconDisabled,
                pressed = iconPressed,
                hovered = iconHovered,
                active = iconNormal,
            )
        },
    )

    @Composable
    fun labelFor(state: TabState) = rememberUpdatedState(
        when {
            state.isSelected -> labelSelected
            else -> state.chooseValueIgnoreCompat(
                normal = labelNormal,
                disabled = labelDisabled,
                pressed = labelPressed,
                hovered = labelHovered,
                active = labelNormal,
            )
        },
    )

    companion object
}

// Tabs are the only components that handle hover states
@Composable
private fun <T> TabState.chooseValueIgnoreCompat(
    normal: T,
    disabled: T,
    pressed: T,
    hovered: T,
    active: T,
): T =
    when {
        !isEnabled -> disabled
        isPressed -> pressed
        isHovered -> hovered
        isActive -> active
        else -> normal
    }

val LocalDefaultTabStyle = staticCompositionLocalOf<TabStyle> {
    error("No LocalTabStyle provided")
}

val LocalEditorTabStyle = staticCompositionLocalOf<TabStyle> {
    error("No LocalTabStyle provided")
}
