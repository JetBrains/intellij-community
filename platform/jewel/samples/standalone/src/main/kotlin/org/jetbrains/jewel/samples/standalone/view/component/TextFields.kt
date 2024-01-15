package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.styling.dark
import org.jetbrains.jewel.intui.standalone.styling.defaults
import org.jetbrains.jewel.intui.standalone.styling.light
import org.jetbrains.jewel.samples.standalone.StandaloneSampleIcons
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider

@Composable
@View(title = "TextFields", position = 9)
fun TextFields() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var text1 by remember { mutableStateOf("TextField") }
        TextField(text1, { text1 = it })

        var text2 by remember { mutableStateOf("") }
        TextField(text2, { text2 = it }, placeholder = { Text("Placeholder") })

        var text3 by remember { mutableStateOf("") }
        TextField(text3, { text3 = it }, outline = Outline.Error, placeholder = { Text("Error outline") })

        var text4 by remember { mutableStateOf("") }
        TextField(text4, { text4 = it }, outline = Outline.Warning, placeholder = { Text("Warning outline") })

        var text5 by remember { mutableStateOf("Disabled") }
        TextField(text5, { text5 = it }, enabled = false)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        var text1 by remember { mutableStateOf("") }
        TextField(
            value = text1,
            onValueChange = { text1 = it },
            placeholder = {
                Text("With leading icon")
            },
            leadingIcon = {
                Icon(
                    resource = "icons/search.svg",
                    contentDescription = "SearchIcon",
                    iconClass = StandaloneSampleIcons::class.java,
                    modifier = Modifier.size(16.dp),
                )
            },
        )

        var text2 by remember { mutableStateOf("") }
        TextField(
            value = text2,
            onValueChange = { text2 = it },
            placeholder = {
                Text("With trailing button")
            },
            trailingIcon = {
                CloseIconButton(text2.isNotEmpty()) { text2 = "" }
            },
        )
    }
}

@Composable
private fun CloseIconButton(
    isVisible: Boolean,
    onClick: () -> Unit,
) {
    Box(Modifier.size(16.dp)) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
        ) {
            // TODO replace when IconButton supports no-background style
            val isDark = JewelTheme.isDark

            val colors = noBackgroundIconButtonColors(isDark)
            val style = remember(isDark, colors) {
                IconButtonStyle(colors, IconButtonMetrics.defaults())
            }

            IconButton(
                onClick,
                style = style,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Default),
            ) { state ->
                val painterProvider =
                    rememberResourcePainterProvider("icons/close.svg", StandaloneSampleIcons::class.java)
                val painter by painterProvider.getPainter(Stateful(state))

                Icon(painter, contentDescription = "Clear")
            }
        }
    }
}

@Composable
private fun noBackgroundIconButtonColors(isDark: Boolean) = if (isDark) {
    IconButtonColors.dark(
        background = Color.Unspecified,
        backgroundDisabled = Color.Unspecified,
        backgroundSelected = Color.Unspecified,
        backgroundSelectedActivated = Color.Unspecified,
        backgroundFocused = Color.Unspecified,
        backgroundPressed = Color.Unspecified,
        backgroundHovered = Color.Unspecified,
    )
} else {
    IconButtonColors.light(
        background = Color.Unspecified,
        backgroundDisabled = Color.Unspecified,
        backgroundSelected = Color.Unspecified,
        backgroundSelectedActivated = Color.Unspecified,
        backgroundFocused = Color.Unspecified,
        backgroundPressed = Color.Unspecified,
        backgroundHovered = Color.Unspecified,
    )
}
