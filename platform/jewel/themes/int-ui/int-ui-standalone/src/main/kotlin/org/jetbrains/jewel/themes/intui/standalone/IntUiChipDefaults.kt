package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ChipDefaults

abstract class IntUiChipDefaults : ChipDefaults {

    @Composable
    override fun shape(): Shape = RoundedCornerShape(100)

    @Composable
    override fun contentPadding(): PaddingValues = PaddingValues(4.dp)

    @Composable
    override fun minWidth(): Dp = 80.dp

    @Composable
    override fun minHeight(): Dp = 40.dp
}
