package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.LinkDefaults
import org.jetbrains.jewel.LinkState

@Suppress("VariableNaming")
abstract class IntUiLinkDefaults : LinkDefaults {

    val Shape = RoundedCornerShape(2.dp)

    @Composable
    override fun haloShape(): Shape = Shape

    @Composable
    override fun externalLinkIconPainter(): Painter = painterResource("intui/externalLink.svg")

    @Composable
    override fun DropdownLinkIconPainter(): Painter = painterResource("intui/chevronBottom.svg")

    @Composable
    override fun textStyle(state: LinkState): State<TextStyle> {
        val defaultTextStyle = IntelliJTheme.defaultTextStyle

        return rememberUpdatedState(
            when {
                !state.isEnabled -> defaultTextStyle
                !state.isHovered && !state.isPressed -> defaultTextStyle
                else -> defaultTextStyle.copy(textDecoration = TextDecoration.Underline)
            }
        )
    }
}
