@file:Suppress("MatchingDeclarationName")

package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.themes.expui.standalone.style.AreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.AreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.HoverAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.InactiveSelectionAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalHoverAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalInactiveAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalNormalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalPressedAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalSelectionAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalSelectionInactiveAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.PressedAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.areaBackground
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme

class TabColors(
    override val normalAreaColors: AreaColors,
    override val selectionAreaColors: AreaColors,
    override val hoverAreaColors: AreaColors,
    override val pressedAreaColors: AreaColors,
    override val inactiveAreaColors: AreaColors,
    override val inactiveSelectionAreaColors: AreaColors
) : AreaProvider, HoverAreaProvider, PressedAreaProvider, InactiveSelectionAreaProvider {

    @Composable
    fun provideArea(selected: Boolean, content: @Composable () -> Unit) {
        val activated = LocalContentActivated.current
        val currentColors = when {
            selected -> if (activated) selectionAreaColors else inactiveSelectionAreaColors
            !activated -> inactiveAreaColors
            else -> normalAreaColors
        }
        CompositionLocalProvider(
            LocalAreaColors provides currentColors,
            LocalNormalAreaColors provides normalAreaColors,
            LocalHoverAreaColors provides hoverAreaColors,
            LocalPressedAreaColors provides pressedAreaColors,
            LocalSelectionInactiveAreaColors provides inactiveSelectionAreaColors,
            LocalInactiveAreaColors provides inactiveAreaColors,
            LocalSelectionAreaColors provides selectionAreaColors,
            content = content
        )
    }
}

val LocalTabColors = compositionLocalOf {
    LightTheme.TabColors
}

val LocalCloseableTabColors = compositionLocalOf {
    LightTheme.CloseableTabColors
}

@Composable
fun Tab(
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: TabColors = LocalTabColors.current,
    content: @Composable RowScope.() -> Unit
) {
    colors.provideArea(selected) {
        val currentColors = LocalAreaColors.current
        Box(
            modifier.areaBackground().drawWithCache {
                onDrawWithContent {
                    drawContent()
                    if (selected) {
                        val strokeWidth = 3.dp.toPx()
                        val start = Offset(strokeWidth / 2f, size.height - strokeWidth / 2f)
                        val end = start.copy(x = size.width - strokeWidth / 2f)
                        drawLine(currentColors.focusColor, start, end, strokeWidth, cap = StrokeCap.Round)
                    }
                }
            }.focusProperties {
                canFocus = false
            }.selectable(
                selected = selected,
                enabled = true,
                onClick = onSelected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = HoverOrPressedIndication(RectangleShape)
            ).padding(horizontal = 12.dp)
        ) {
            Row(modifier = Modifier.align(Alignment.Center), content = content)
        }
    }
}

@Composable
fun CloseableTab(
    selected: Boolean,
    onSelected: () -> Unit,
    onClosed: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    colors: TabColors = LocalCloseableTabColors.current,
    content: @Composable RowScope.() -> Unit
) {
    colors.provideArea(selected) {
        val currentColors = LocalAreaColors.current
        var hover by remember { mutableStateOf(false) }
        Box(
            modifier.areaBackground().drawWithCache {
                onDrawWithContent {
                    drawContent()
                    if (selected) {
                        val strokeWidth = 3.dp.toPx()
                        val start = Offset(strokeWidth / 2f, size.height - strokeWidth / 2f)
                        val end = start.copy(x = size.width - strokeWidth / 2f)
                        drawLine(currentColors.focusColor, start, end, strokeWidth, cap = StrokeCap.Round)
                    }
                }
            }.focusProperties {
                canFocus = false
            }.selectable(
                selected = selected,
                enabled = true,
                onClick = onSelected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = HoverOrPressedIndication(RectangleShape)
            ).onHover {
                hover = it
            }.padding(horizontal = 12.dp)
        ) {
            @Suppress("MagicNumber")
            Row(
                modifier = Modifier.align(Alignment.Center).graphicsLayer(alpha = if (hover || selected) 1f else 0.7f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
                CloseButton(hover || selected, onClosed)
            }
        }
    }
}

@Composable
private fun CloseButton(
    shown: Boolean,
    onClosed: () -> Unit,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    var hover by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.size(16.dp).areaBackground().focusProperties {
            canFocus = false
        }.clickable(
            enabled = shown,
            onClick = onClosed,
            role = Role.Button,
            interactionSource = interactionSource,
            indication = null
        ).onHover {
            hover = it
        }
    ) {
        if (shown) {
            Icon(
                if (hover) "icons/closeSmallHovered.svg" else "icons/closeSmall.svg",
                contentDescription = "Close"
            )
        }
    }
}
