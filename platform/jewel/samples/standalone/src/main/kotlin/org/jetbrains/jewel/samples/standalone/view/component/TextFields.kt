package org.jetbrains.jewel.samples.standalone.view.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.samples.standalone.StandaloneSampleIcons
import org.jetbrains.jewel.samples.standalone.viewmodel.View
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
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
            text1,
            { text1 = it },
            enabled = true,
            leadingIcon = {
                Icon(
                    resource = "icons/search.svg",
                    contentDescription = "SearchIcon",
                    iconClass = StandaloneSampleIcons::class.java,
                    modifier = Modifier.size(16.dp),
                )
            },
            placeholder = {
                Text("With leading icon")
            },
        )

        var text2 by remember { mutableStateOf("") }
        TextField(
            text2,
            { text2 = it },
            enabled = true,
            trailingIcon = {
                AnimatedVisibility(
                    visible = text2.isNotEmpty(),
                    enter = fadeIn() + slideInHorizontally { it / 2 },
                    exit = fadeOut() + slideOutHorizontally { it / 2 },
                ) {
                    CloseIconButton { text2 = "" }
                }
            },
            placeholder = {
                Text("With trailing button")
            },
        )
    }
}

@Composable
private fun CloseIconButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    var hovered by remember { mutableStateOf(false) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            when (it) {
                is HoverInteraction.Enter -> hovered = true
                is HoverInteraction.Exit -> hovered = false
            }
        }
    }

    val closeIconProvider = rememberResourcePainterProvider("icons/close.svg", StandaloneSampleIcons::class.java)
    val closeIcon by closeIconProvider.getPainter()

    val hoveredCloseIconProvider =
        rememberResourcePainterProvider("icons/closeHovered.svg", StandaloneSampleIcons::class.java)
    val hoveredCloseIcon by hoveredCloseIconProvider.getPainter()

    Icon(
        painter = if (hovered) hoveredCloseIcon else closeIcon,
        contentDescription = "Clear",
        modifier = Modifier
            .pointerHoverIcon(PointerIcon.Default)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
            ) { onClick() },
    )
}
