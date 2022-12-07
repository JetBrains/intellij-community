package org.jetbrains.jewel.styles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.BottomLineShape
import org.jetbrains.jewel.IntelliJPalette
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.components.state.TabState

typealias TabStyle = ControlStyle<TabAppearance, TabState>

@Immutable
data class TabAppearance(
    val textStyle: TextStyle = TextStyle.Default,
    val backgroundColor: Color = Color.Unspecified,
    val shapeStroke: ShapeStroke<*>? = null,
    val shape: Shape = RectangleShape,

    val contentPadding: PaddingValues = PaddingValues(16.dp, 8.dp),
    val contentArrangement: Arrangement.Horizontal = Arrangement.Start,
    val contentAlignment: Alignment.Vertical = Alignment.Top,

    val adornmentStroke: ShapeStroke<*>? = null,
    val adornmentShape: Shape? = null,
    val minWidth: Dp = 64.dp,
    val minHeight: Dp = 32.dp
)

val LocalTabStyle = compositionLocalOf<TabStyle> { localNotProvided() }
val Styles.tab: TabStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalTabStyle.current

fun TabStyle(palette: IntelliJPalette, typography: TextStyle): TabStyle = TabStyle {
    variation(Orientation.Horizontal) {
        state(
            TabState.Normal,
            TabAppearance(
                contentAlignment = Alignment.Bottom,
                contentArrangement = Arrangement.Center,
                textStyle = typography.copy(palette.text)
            )
        )
        state(
            TabState.Hovered,
            TabAppearance(
                contentAlignment = Alignment.Bottom,
                contentArrangement = Arrangement.Center,
                textStyle = typography.copy(palette.text),
                backgroundColor = palette.tab.hoveredBackgroundColor
            )
        )
        state(
            TabState.Selected,
            TabAppearance(
                contentAlignment = Alignment.Bottom,
                contentArrangement = Arrangement.Center,
                textStyle = typography.copy(palette.text),
                adornmentShape = BottomLineShape,
                adornmentStroke = ShapeStroke.SolidColor(3.dp, palette.tab.underlineColor)
            )
        )
        state(
            TabState.SelectedAndHovered,
            TabAppearance(
                contentAlignment = Alignment.Bottom,
                contentArrangement = Arrangement.Center,
                textStyle = typography.copy(palette.text),
                backgroundColor = palette.tab.hoveredBackgroundColor,
                adornmentShape = BottomLineShape,
                adornmentStroke = ShapeStroke.SolidColor(3.dp, palette.tab.underlineColor)
            )
        )
    }

    variation(Orientation.Vertical) {
        state(
            TabState.Normal,
            TabAppearance(
                contentAlignment = Alignment.CenterVertically,
                contentArrangement = Arrangement.Start,
                textStyle = typography.copy(palette.text),
                backgroundColor = palette.background
            )
        )
        state(
            TabState.Selected,
            TabAppearance(
                contentAlignment = Alignment.CenterVertically,
                contentArrangement = Arrangement.Start,
                textStyle = typography.copy(palette.text),
                adornmentStroke = ShapeStroke.SolidColor(1.dp, palette.tab.underlineColor)
            )
        )
        state(
            TabState.Hovered,
            TabAppearance(
                contentAlignment = Alignment.CenterVertically,
                contentArrangement = Arrangement.Start,
                textStyle = typography.copy(palette.text),
                backgroundColor = palette.tab.hoveredBackgroundColor
            )
        )
    }
}
