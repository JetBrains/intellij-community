package org.jetbrains.jewel

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

@Composable
fun GroupHeader(
    text: String,
    modifier: Modifier = Modifier,
    defaults: GroupHeaderDefaults = IntelliJTheme.groupHeaderDefaults,
    colors: GroupHeaderColors = defaults.colors()
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(
            LocalTextColor.provides(colors.textColor().value)
        ) {
            Text(text)
            Divider(
                color = colors.dividerColor().value,
                orientation = Orientation.Horizontal,
                startIndent = defaults.indent()
            )
        }
    }
}

interface GroupHeaderColors {

    @Composable
    fun dividerColor(): State<Color>

    @Composable
    fun textColor(): State<Color>
}

interface GroupHeaderDefaults {

    @Composable
    fun colors(): GroupHeaderColors

    @Composable
    fun indent(): Dp

    @Composable
    fun dividerThickness(): Dp
}

fun groupHeaderColors(
    dividerColor: Color,
    textColor: Color
): GroupHeaderColors = DefaultGroupHeaderColors(
    dividerColor = dividerColor,
    textColor = textColor
)

private class DefaultGroupHeaderColors(
    private val dividerColor: Color,
    private val textColor: Color
) : GroupHeaderColors {

    @Composable
    override fun dividerColor(): State<Color> = rememberUpdatedState(dividerColor)

    @Composable
    override fun textColor(): State<Color> = rememberUpdatedState(textColor)
}

internal val LocalGroupHeaderDefaults = staticCompositionLocalOf<GroupHeaderDefaults> {
    error("LocalGroupHeaderDefaults not provided")
}
