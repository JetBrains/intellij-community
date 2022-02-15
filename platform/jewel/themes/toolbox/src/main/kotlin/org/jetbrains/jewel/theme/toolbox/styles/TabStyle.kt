package org.jetbrains.jewel.theme.toolbox.styles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.jetbrains.jewel.Insets
import org.jetbrains.jewel.Orientation
import org.jetbrains.jewel.ShapeStroke
import org.jetbrains.jewel.components.state.TabState
import org.jetbrains.jewel.styles.ControlStyle
import org.jetbrains.jewel.styles.Styles
import org.jetbrains.jewel.styles.localNotProvided
import org.jetbrains.jewel.theme.toolbox.Palette
import org.jetbrains.jewel.theme.toolbox.ToolboxMetrics
import org.jetbrains.jewel.theme.toolbox.ToolboxTypography
import org.jetbrains.jewel.toBrush

typealias TabStyle = ControlStyle<TabAppearance, TabState>

@Immutable
data class TabAppearance(
    val textStyle: TextStyle = TextStyle.Default,
    val backgroundColor: Color = Color.Unspecified,
    val shapeStroke: ShapeStroke? = null,
    val shape: Shape = RectangleShape,

    val contentPadding: PaddingValues = PaddingValues(16.dp, 8.dp),
    val contentArrangement: Arrangement.Horizontal = Arrangement.Start,
    val contentAlignment: Alignment.Vertical = Alignment.Top,

    val adornmentStroke: ShapeStroke? = null,
    val adornmentShape: Shape? = null,
    val minWidth: Dp = 64.dp,
    val minHeight: Dp = 32.dp,
)

val LocalTabStyle = compositionLocalOf<TabStyle> { localNotProvided() }
val Styles.tab: TabStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalTabStyle.current

fun TabStyle(palette: Palette, metrics: ToolboxMetrics, typography: ToolboxTypography): TabStyle = TabStyle {
    variation(Orientation.Horizontal) {
        state(
            TabState.Normal,
            TabAppearance(
                contentAlignment = Alignment.Bottom,
                contentArrangement = Arrangement.Center,
                textStyle = typography.control.copy(palette.text),
                backgroundColor = Color.Unspecified,
                adornmentStroke = ShapeStroke(
                    metrics.adornmentsThickness,
                    palette.text.toBrush(),
                    Insets(0.dp, metrics.adornmentsThickness / 2)
                ),
            )
        )
        state(
            TabState.Hovered,
            TabAppearance(
                contentAlignment = Alignment.Bottom,
                contentArrangement = Arrangement.Center,
                textStyle = typography.control.copy(palette.text),
                backgroundColor = Color.Unspecified,
                adornmentShape = BottomLineShape,
                adornmentStroke = ShapeStroke(
                    metrics.adornmentsThickness,
                    palette.controlAdornmentsHover.toBrush(),
                    Insets(0.dp, metrics.adornmentsThickness / 2)
                ),
            )
        )
        state(
            TabState.Selected,
            TabAppearance(
                contentAlignment = Alignment.Bottom,
                contentArrangement = Arrangement.Center,
                textStyle = typography.control.copy(palette.textActive),
                backgroundColor = Color.Unspecified,
                adornmentShape = BottomLineShape,
                adornmentStroke = ShapeStroke(
                    metrics.adornmentsThickness,
                    palette.controlAdornmentsActive.toBrush(),
                    Insets(0.dp, metrics.adornmentsThickness / 2)
                ),
            )
        )
    }

    variation(Orientation.Vertical) {
        state(
            TabState.Normal,
            TabAppearance(
                contentAlignment = Alignment.CenterVertically,
                contentArrangement = Arrangement.Start,
                textStyle = typography.control.copy(palette.text),
                backgroundColor = Color.Unspecified,
                shape = RoundedCornerShape(metrics.cornerSize),
            )
        )
        state(
            TabState.Selected,
            TabAppearance(
                contentAlignment = Alignment.CenterVertically,
                contentArrangement = Arrangement.Start,
                textStyle = typography.control.copy(palette.controlContent),
                backgroundColor = palette.controlBackground,
                shape = RoundedCornerShape(metrics.cornerSize),
            )
        )
        state(
            TabState.Hovered,
            TabAppearance(
                contentAlignment = Alignment.CenterVertically,
                contentArrangement = Arrangement.Start,
                textStyle = typography.control.copy(palette.text),
                backgroundColor = palette.controlAdornmentsHover,
                shape = RoundedCornerShape(metrics.cornerSize),
            )
        )
    }
}
