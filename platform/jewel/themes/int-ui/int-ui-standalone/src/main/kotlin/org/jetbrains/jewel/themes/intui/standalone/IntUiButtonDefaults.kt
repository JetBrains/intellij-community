package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ButtonDefaults

@Suppress("VariableNaming")
abstract class IntUiButtonDefaults : ButtonDefaults {

    private val ButtonHorizontalPadding = 12.dp
    private val ButtonVerticalPadding = 6.dp

    val ContentPadding = PaddingValues(
        start = ButtonHorizontalPadding,
        top = ButtonVerticalPadding,
        end = ButtonHorizontalPadding,
        bottom = ButtonVerticalPadding
    )

    val MinWidth = 72.dp

    val MinHeight = 28.dp

    val Shape = RoundedCornerShape(4.dp)

    @Composable
    override fun shape(): RoundedCornerShape = Shape

    @Composable
    override fun contentPadding(): PaddingValues = ContentPadding

    @Composable
    override fun minWidth(): Dp = MinWidth

    @Composable
    override fun minHeight(): Dp = MinHeight
}
