package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.IntelliJTheme
import org.jetbrains.jewel.TextAreaDefaults

@Suppress("VariableNaming")
abstract class IntUiTextAreaDefaults : TextAreaDefaults {

    val Shape = RoundedCornerShape(4.dp)

    val HintShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 4.dp,
        bottomEnd = 4.dp
    )

    val VerticalPadding = 6.dp

    val HintVerticalPadding = 4.dp

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

    @Composable
    override fun hintShape(): Shape = HintShape

    @Composable
    override fun hintContentPadding(): PaddingValues = PaddingValues(
        horizontal = HorizontalPadding,
        vertical = HintVerticalPadding
    )

    @Composable
    override fun hintTextStyle(): TextStyle {
        return IntelliJTheme.defaultTextStyle.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
