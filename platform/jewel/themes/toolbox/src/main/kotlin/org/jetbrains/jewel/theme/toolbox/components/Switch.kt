package org.jetbrains.jewel.theme.toolbox.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import org.jetbrains.jewel.NoIndication
import org.jetbrains.jewel.shape
import org.jetbrains.jewel.theme.toolbox.styles.LocalSwitchStyle
import org.jetbrains.jewel.theme.toolbox.styles.SwitchStyle

enum class SwitchState {
    On,
    Off
}

@Composable
fun Switch(
    checked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: SwitchStyle = LocalSwitchStyle.current,
) {
    val appearance = when (checked) {
        true -> style.appearance(SwitchState.On)
        false -> style.appearance(SwitchState.Off)
    }

    val shapeModifier = if (appearance.backgroundColor != Color.Unspecified) {
        val backgroundAnimate by animateColorAsState(appearance.backgroundColor)
        Modifier.shape(appearance.shape, appearance.shapeStroke, backgroundAnimate)
    } else
        Modifier
    val thumbShapeModifier =
        if (appearance.thumbBackgroundColor != Color.Unspecified)
            Modifier.shape(appearance.thumbShape, appearance.thumbBorderStroke, appearance.thumbBackgroundColor)
        else
            Modifier

    val thumbPosition by animateDpAsState(
        when {
            !checked -> appearance.thumbPadding
            else -> appearance.width - appearance.thumbSize - appearance.thumbPadding
        }
    )

    Box(
        modifier
            .size(appearance.width, appearance.height)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                indication = NoIndication,
                interactionSource = interactionSource,
                onValueChange = onCheckedChange
            )
            .then(shapeModifier)
            .clip(appearance.shape)
    )
    {
        Box(
            Modifier
                .size(appearance.thumbSize)
                .offset(thumbPosition, (appearance.height - appearance.thumbSize) / 2)
                .then(thumbShapeModifier)

        )
    }
}
