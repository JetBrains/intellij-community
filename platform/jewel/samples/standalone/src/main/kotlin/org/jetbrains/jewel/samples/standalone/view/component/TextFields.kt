package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.styling.IconButtonColors
import org.jetbrains.jewel.ui.component.styling.IconButtonMetrics
import org.jetbrains.jewel.ui.component.styling.IconButtonStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.painter.hints.Stateful

@Composable
fun TextFields() {
    VerticallyScrollableContainer(Modifier.fillMaxSize()) {
        Column {
            TextFieldsRows(readOnly = false)

            Spacer(Modifier.height(16.dp))

            GroupHeader("Read-only")

            Spacer(Modifier.height(16.dp))

            TextFieldsRows(readOnly = true)
        }
    }
}

@Composable
private fun TextFieldsRows(readOnly: Boolean) {
    Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val state1 = rememberTextFieldState("TextField")
            TextField(state = state1, modifier = Modifier.width(200.dp), readOnly = readOnly)

            val state2 = rememberTextFieldState("")
            TextField(
                state = state2,
                placeholder = { Text("Placeholder") },
                modifier = Modifier.width(200.dp),
                readOnly = readOnly,
            )

            val state3 = rememberTextFieldState("")
            TextField(
                state = state3,
                outline = Outline.Error,
                placeholder = { Text("Error outline") },
                modifier = Modifier.width(200.dp),
                readOnly = readOnly,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val state1 = rememberTextFieldState("")
            TextField(
                state = state1,
                outline = Outline.Warning,
                placeholder = { Text("Warning outline") },
                modifier = Modifier.width(200.dp),
                readOnly = readOnly,
            )

            val state2 = rememberTextFieldState("Disabled")
            TextField(state = state2, enabled = false, modifier = Modifier.width(200.dp), readOnly = readOnly)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            val state1 = rememberTextFieldState("")
            TextField(
                state = state1,
                placeholder = { Text("With leading icon") },
                modifier = Modifier.width(200.dp),
                leadingIcon = {
                    Icon(
                        key = AllIconsKeys.Actions.Find,
                        contentDescription = "SearchIcon",
                        iconClass = StandaloneSampleIcons::class.java,
                        modifier = Modifier.size(16.dp),
                    )
                },
                readOnly = readOnly,
            )

            val state2 = rememberTextFieldState("")
            TextField(
                state = state2,
                placeholder = { Text("With trailing button") },
                modifier = Modifier.width(200.dp),
                trailingIcon = {
                    CloseIconButton(
                        isVisible = state2.text.isNotEmpty(),
                        onClick = { state2.setTextAndPlaceCursorAtEnd("") },
                    )
                },
                readOnly = readOnly,
            )
        }
    }
}

@Composable
private fun CloseIconButton(isVisible: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(16.dp)) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
        ) {
            // TODO replace when IconButton supports no-background style
            val isDark = JewelTheme.isDark

            val colors = noBackgroundIconButtonColors(isDark)
            val style = remember(isDark, colors) { IconButtonStyle(colors, IconButtonMetrics.defaults()) }

            IconButton(onClick, style = style, modifier = Modifier.pointerHoverIcon(PointerIcon.Default)) { state ->
                Icon(AllIconsKeys.General.Close, contentDescription = "Clear", hint = Stateful(state))
            }
        }
    }
}

@Composable
private fun noBackgroundIconButtonColors(isDark: Boolean) =
    if (isDark) {
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
