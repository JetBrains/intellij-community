@file:Suppress("MatchingDeclarationName")
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
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDisabledAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalHoverAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalNormalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalPressedAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.PressedAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.areaBackground
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme

class ActionButtonColors(
    override val normalAreaColors: AreaColors,
    override val hoverAreaColors: AreaColors,
    override val pressedAreaColors: AreaColors,
    override val disabledAreaColors: AreaColors,
) : AreaProvider, HoverAreaProvider, PressedAreaProvider, DisabledAreaProvider {

    @Composable
    fun provideArea(enabled: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalAreaColors provides if (enabled) normalAreaColors else disabledAreaColors,
            LocalNormalAreaColors provides normalAreaColors,
            LocalDisabledAreaColors provides disabledAreaColors,
            LocalHoverAreaColors provides hoverAreaColors,
            LocalPressedAreaColors provides pressedAreaColors,
            content = content
        )
    }
}

val LocalActionButtonColors = compositionLocalOf {
    LightTheme.ActionButtonColors
}

@Composable
fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(6.dp),
    indication: Indication? = HoverOrPressedIndication(shape),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: ActionButtonColors = LocalActionButtonColors.current,
    content: @Composable BoxScope.() -> Unit,
) {
    colors.provideArea(enabled) {
        Box(
            modifier.areaBackground(shape = shape).clickable(
                interactionSource = interactionSource,
                indication = indication,
                enabled = enabled,
                onClick = onClick,
                role = Role.Button
            ),
            propagateMinConstraints = true
        ) {
            content()
        }
    }
}
