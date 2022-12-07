package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.themes.expui.standalone.style.AreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.AreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.DisabledAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.HoverAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.InactiveSelectionAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDisabledAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalHoverAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalInactiveAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalNormalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalPressedAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalSelectionAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalSelectionInactiveAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.PressedAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.areaBackground
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme

class ToolBarActionButtonColors(
    override val normalAreaColors: AreaColors,
    override val hoverAreaColors: AreaColors,
    override val pressedAreaColors: AreaColors,
    override val disabledAreaColors: AreaColors,
    override val selectionAreaColors: AreaColors,
    override val inactiveAreaColors: AreaColors,
    override val inactiveSelectionAreaColors: AreaColors,
) : AreaProvider, HoverAreaProvider, PressedAreaProvider, DisabledAreaProvider, InactiveSelectionAreaProvider {

    @Composable
    fun provideArea(enabled: Boolean, selected: Boolean, content: @Composable () -> Unit) {
        val activated = LocalContentActivated.current
        val currentColors = when {
            !enabled -> disabledAreaColors
            selected -> if (activated) selectionAreaColors else inactiveSelectionAreaColors
            !activated -> inactiveAreaColors
            else -> normalAreaColors
        }
        CompositionLocalProvider(
            LocalAreaColors provides currentColors,
            LocalNormalAreaColors provides normalAreaColors,
            LocalDisabledAreaColors provides disabledAreaColors,
            LocalHoverAreaColors provides hoverAreaColors,
            LocalPressedAreaColors provides pressedAreaColors,
            LocalSelectionInactiveAreaColors provides inactiveSelectionAreaColors,
            LocalInactiveAreaColors provides inactiveAreaColors,
            LocalSelectionAreaColors provides selectionAreaColors,
            content = content
        )
    }
}

val LocalToolBarActionButtonColors = compositionLocalOf {
    LightTheme.ToolBarActionButtonColors
}

@Composable
fun ToolBarActionButton(
    selected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(6.dp),
    indication: Indication? = HoverOrPressedIndication(shape),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: ToolBarActionButtonColors = LocalToolBarActionButtonColors.current,
    content: @Composable BoxScope.() -> Unit,
) {
    colors.provideArea(enabled, selected) {
        Box(
            modifier.clickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = enabled,
                onClick = onClick,
                role = Role.Button
            ).areaBackground(shape = shape),
            propagateMinConstraints = true
        ) {
            content()
        }
    }
}
