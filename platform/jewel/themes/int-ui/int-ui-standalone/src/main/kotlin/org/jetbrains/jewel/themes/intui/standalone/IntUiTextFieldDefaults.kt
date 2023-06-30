package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.TextFieldDefaults

@Suppress("VariableNaming")
abstract class IntUiTextFieldDefaults : TextFieldDefaults {

    val Shape = RoundedCornerShape(4.dp)

    val VerticalPadding = 6.dp

    val HorizontalPadding = 9.dp

    val MinHeight = 28.dp

    val MinWidth = 144.dp

    @Composable
    override fun shape(): Shape = Shape

    @Composable
    override fun textStyle(): TextStyle = IntelliJTheme.defaultTextStyle

    @Composable
    override fun contentPadding(): PaddingValues = PaddingValues(
        horizontal = HorizontalPadding,
        vertical = VerticalPadding
    )

    @Composable
    override fun minHeight(): Dp = MinHeight

    @Composable
    override fun minWidth(): Dp = MinWidth
}
