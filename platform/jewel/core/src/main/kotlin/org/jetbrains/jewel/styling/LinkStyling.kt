package org.jetbrains.jewel.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.LinkState
import org.jetbrains.jewel.painter.PainterProvider

@Immutable
interface LinkStyle {

    val colors: LinkColors
    val metrics: LinkMetrics
    val icons: LinkIcons
    val textStyles: LinkTextStyles
}

@Immutable
interface LinkColors {

    val content: Color
    val contentDisabled: Color
    val contentFocused: Color
    val contentPressed: Color
    val contentHovered: Color
    val contentVisited: Color

    @Composable
    fun contentFor(state: LinkState) = rememberUpdatedState(
        state.chooseValueWithVisited(
            normal = content,
            disabled = contentDisabled,
            focused = contentFocused,
            pressed = contentPressed,
            hovered = contentHovered,
            visited = contentVisited,
            active = content,
        ),
    )
}

@Immutable
interface LinkMetrics {

    val focusHaloCornerSize: CornerSize
    val textIconGap: Dp
    val iconSize: DpSize
}

@Immutable
interface LinkIcons {

    val dropdownChevron: PainterProvider
    val externalLink: PainterProvider
}

@Immutable
interface LinkTextStyles {

    val normal: TextStyle
    val disabled: TextStyle
    val focused: TextStyle
    val pressed: TextStyle
    val hovered: TextStyle
    val visited: TextStyle

    @Composable
    fun styleFor(state: LinkState) = rememberUpdatedState(
        state.chooseValueWithVisited(
            normal = normal,
            disabled = disabled,
            focused = focused,
            pressed = pressed,
            hovered = hovered,
            visited = visited,
            active = normal,
        ),
    )
}

val LocalLinkStyle = staticCompositionLocalOf<LinkStyle> {
    error("No LinkStyle provided")
}
