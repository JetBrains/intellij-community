@file:Suppress("MatchingDeclarationName")

package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.themes.expui.standalone.style.AreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.AreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalNormalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.areaBackground
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import org.jetbrains.jewel.themes.expui.standalone.theme.LocalIsDarkTheme

class ToolTipColors(
    val isDark: Boolean,
    override val normalAreaColors: AreaColors
) : AreaProvider {

    @Composable
    fun provideArea(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalAreaColors provides normalAreaColors,
            LocalNormalAreaColors provides normalAreaColors,
            LocalIsDarkTheme provides isDark,
            content = content
        )
    }
}

val LocalToolTipColors = compositionLocalOf {
    LightTheme.ToolTipColors
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun Tooltip(
    tooltip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    delayMillis: Int = 500,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.CursorPoint(
        offset = DpOffset(0.dp, 32.dp)
    ),
    colors: ToolTipColors = LocalToolTipColors.current,
    content: @Composable () -> Unit
) {
    TooltipArea(
        {
            colors.provideArea {
                Box(
                    modifier = Modifier.shadow(8.dp).areaBackground()
                        .border(1.dp, LocalAreaColors.current.startBorderColor),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        tooltip()
                    }
                }
            }
        },
        modifier,
        delayMillis,
        tooltipPlacement,
        content
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun Tooltip(
    tooltip: String,
    modifier: Modifier = Modifier,
    delayMillis: Int = 500,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.CursorPoint(
        offset = DpOffset(0.dp, 32.dp)
    ),
    colors: ToolTipColors = LocalToolTipColors.current,
    content: @Composable () -> Unit
) {
    Tooltip({
        Label(tooltip)
    }, modifier, delayMillis, tooltipPlacement, colors, content)
}
