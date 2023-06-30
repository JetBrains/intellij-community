package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.GroupHeaderDefaults

@Suppress("VariableNaming")
abstract class IntUiGroupHeaderDefaults : GroupHeaderDefaults {

    val Indent = 8.dp

    val DividerThickness = 1.dp

    @Composable
    override fun indent(): Dp = Indent

    @Composable
    override fun dividerThickness(): Dp = DividerThickness
}
