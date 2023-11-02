package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.component.LinkState
import org.jetbrains.jewel.ui.painter.PainterProvider

@Immutable
@GenerateDataFunctions
public class LinkStyle(
    public val colors: LinkColors,
    public val metrics: LinkMetrics,
    public val icons: LinkIcons,
    public val textStyles: LinkTextStyles,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class LinkColors(
    public val content: Color,
    public val contentDisabled: Color,
    public val contentFocused: Color,
    public val contentPressed: Color,
    public val contentHovered: Color,
    public val contentVisited: Color,
) {

    @Composable
    public fun contentFor(state: LinkState): State<Color> =
        rememberUpdatedState(
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

    public companion object
}

@Immutable
@GenerateDataFunctions
public class LinkMetrics(
    public val focusHaloCornerSize: CornerSize,
    public val textIconGap: Dp,
    public val iconSize: DpSize,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class LinkIcons(
    public val dropdownChevron: PainterProvider,
    public val externalLink: PainterProvider,
) {

    public companion object
}

@Immutable
@GenerateDataFunctions
public class LinkTextStyles(
    public val normal: TextStyle,
    public val disabled: TextStyle,
    public val focused: TextStyle,
    public val pressed: TextStyle,
    public val hovered: TextStyle,
    public val visited: TextStyle,
) {

    @Composable
    public fun styleFor(state: LinkState): State<TextStyle> =
        rememberUpdatedState(
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

    public companion object
}

public val LocalLinkStyle: ProvidableCompositionLocal<LinkStyle> =
    staticCompositionLocalOf {
        error("No LinkStyle provided. Have you forgotten the theme?")
    }
