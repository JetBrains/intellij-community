package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.DropdownDefaults
import org.jetbrains.jewel.IntelliJTheme

@Suppress("VariableNaming")
abstract class IntUiDropdownDefaults : DropdownDefaults, IntUiMenuDefaults() {

    private val Shape = RoundedCornerShape(4.dp)

    private val ContentPadding = PaddingValues(start = 9.dp, end = 8.dp)

    private val MinWidth = 108.dp

    private val MinHeight = 28.dp

    @Composable
    override fun chevronPainter(): Painter = painterResource("intui/chevronBottom.svg")

    @Composable
    override fun shape(): Shape = Shape

    @Composable
    override fun textStyle(): TextStyle = IntelliJTheme.defaultTextStyle

    @Composable
    override fun contentPadding(): PaddingValues = ContentPadding

    @Composable
    override fun minWidth(): Dp = MinWidth

    @Composable
    override fun minHeight(): Dp = MinHeight
}
