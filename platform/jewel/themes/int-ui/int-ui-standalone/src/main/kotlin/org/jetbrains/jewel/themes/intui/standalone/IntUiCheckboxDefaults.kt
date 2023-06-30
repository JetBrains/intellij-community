package org.jetbrains.jewel.themes.intui.standalone

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.CheckboxDefaults
import org.jetbrains.jewel.CheckboxState
import org.jetbrains.jewel.IntelliJTheme

@Suppress("VariableNaming")
abstract class IntUiCheckboxDefaults : CheckboxDefaults {

    val CheckboxSize = 16.dp

    val Shape = RoundedCornerShape(3.dp)

    val ContentSpacing = 8.dp

    @Composable
    override fun width(): Dp = CheckboxSize

    @Composable
    override fun height(): Dp = CheckboxSize

    @Composable
    override fun shape(): Shape = Shape

    @Composable
    override fun contentSpacing(): Dp = ContentSpacing

    @Composable
    override fun textStyle(): TextStyle = IntelliJTheme.defaultTextStyle

    @Composable
    override fun checkmark(state: CheckboxState): State<Painter?> {
        val checked = painterResource("intui/checkboxCheckmarkOn.svg")
        val indeterminate = painterResource("intui/checkboxCheckmarkIndeterminate.svg")

        return rememberUpdatedState(
            when (state.toggle) {
                ToggleableState.On -> checked
                ToggleableState.Off -> null
                ToggleableState.Indeterminate -> indeterminate
            }
        )
    }
}
