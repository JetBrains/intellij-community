package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.TabState
import org.jetbrains.jewel.ui.painter.PainterProvider

@Stable
@GenerateDataFunctions
public class TabStyle(
    public val colors: TabColors,
    public val metrics: TabMetrics,
    public val icons: TabIcons,
    public val contentAlpha: TabContentAlpha,
) {

    public companion object
}

@Stable
@GenerateDataFunctions
public class TabMetrics(
    public val underlineThickness: Dp,
    public val tabPadding: PaddingValues,
    public val tabHeight: Dp,
    public val tabContentSpacing: Dp,
    public val closeContentGap: Dp,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class TabIcons(public val close: PainterProvider) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class TabColors(
    public val background: Color,
    public val backgroundDisabled: Color,
    public val backgroundPressed: Color,
    public val backgroundHovered: Color,
    public val backgroundSelected: Color,
    public val content: Color,
    public val contentDisabled: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val contentSelected: Color,
    public val underline: Color,
    public val underlineDisabled: Color,
    public val underlinePressed: Color,
    public val underlineHovered: Color,
    public val underlineSelected: Color,
) {

    @Composable
    public fun contentFor(state: TabState): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected -> contentSelected
                else ->
                    state.chooseValueIgnoreCompat(
                        normal = content,
                        disabled = contentDisabled,
                        pressed = contentPressed,
                        hovered = contentHovered,
                        active = content,
                    )
            },
        )

    @Composable
    public fun backgroundFor(state: TabState): State<Color> =
        rememberUpdatedState(
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
    public fun underlineFor(state: TabState): State<Color> =
        rememberUpdatedState(
            when {
                state.isSelected -> underlineSelected
                else ->
                    state.chooseValueIgnoreCompat(
                        normal = underline,
                        disabled = underlineDisabled,
                        pressed = underlinePressed,
                        hovered = underlineHovered,
                        active = underline,
                    )
            },
        )

    public companion object
}

@Immutable
@GenerateDataFunctions
public class TabContentAlpha(
    public val iconNormal: Float,
    public val iconDisabled: Float,
    public val iconPressed: Float,
    public val iconHovered: Float,
    public val iconSelected: Float,
    public val contentNormal: Float,
    public val contentDisabled: Float,
    public val contentPressed: Float,
    public val contentHovered: Float,
    public val contentSelected: Float,
) {

    @Composable
    public fun iconFor(state: TabState): State<Float> =
        rememberUpdatedState(
            when {
                state.isSelected -> iconSelected
                else ->
                    state.chooseValueIgnoreCompat(
                        normal = iconNormal,
                        disabled = iconDisabled,
                        pressed = iconPressed,
                        hovered = iconHovered,
                        active = iconNormal,
                    )
            },
        )

    @Composable
    public fun contentFor(state: TabState): State<Float> =
        rememberUpdatedState(
            when {
                state.isSelected -> contentSelected
                else ->
                    state.chooseValueIgnoreCompat(
                        normal = contentNormal,
                        disabled = contentDisabled,
                        pressed = contentPressed,
                        hovered = contentHovered,
                        active = contentNormal,
                    )
            },
        )

    public companion object
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

public val LocalDefaultTabStyle: ProvidableCompositionLocal<TabStyle> =
    staticCompositionLocalOf {
        error("No LocalTabStyle provided. Have you forgotten the theme?")
    }

public val LocalEditorTabStyle: ProvidableCompositionLocal<TabStyle> =
    staticCompositionLocalOf {
        error("No LocalTabStyle provided. Have you forgotten the theme?")
    }
