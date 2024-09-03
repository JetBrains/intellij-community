package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import java.awt.Cursor
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.state.CommonStateBitMask
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.JewelTheme.Companion.isSwingCompatMode
import org.jetbrains.jewel.ui.component.styling.LinkStyle
import org.jetbrains.jewel.ui.component.styling.LinkUnderlineBehavior.ShowAlways
import org.jetbrains.jewel.ui.component.styling.LinkUnderlineBehavior.ShowOnHover
import org.jetbrains.jewel.ui.component.styling.LocalLinkStyle
import org.jetbrains.jewel.ui.component.styling.LocalMenuStyle
import org.jetbrains.jewel.ui.component.styling.MenuStyle
import org.jetbrains.jewel.ui.disabled
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.util.thenIf

@Composable
public fun Link(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    overflow: TextOverflow = TextOverflow.Clip,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: LinkStyle = LocalLinkStyle.current,
) {
    LinkImpl(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        overflow = overflow,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        icon = null,
    )
}

@Composable
public fun ExternalLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    overflow: TextOverflow = TextOverflow.Clip,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: LinkStyle = LocalLinkStyle.current,
) {
    LinkImpl(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        overflow = overflow,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        icon = style.icons.externalLink,
    )
}

@Composable
public fun DropdownLink(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    overflow: TextOverflow = TextOverflow.Clip,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: LinkStyle = LocalLinkStyle.current,
    menuModifier: Modifier = Modifier,
    menuStyle: MenuStyle = LocalMenuStyle.current,
    menuContent: MenuScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    var skipNextClick by remember { mutableStateOf(false) }

    Box(Modifier.onHover { hovered = it }) {
        LinkImpl(
            text = text,
            onClick = {
                if (!skipNextClick) {
                    expanded = !expanded
                }
                skipNextClick = false
            },
            modifier = modifier,
            enabled = enabled,
            overflow = overflow,
            interactionSource = interactionSource,
            style = style,
            textStyle = textStyle,
            icon = style.icons.dropdownChevron,
        )

        if (expanded) {
            PopupMenu(
                onDismissRequest = {
                    expanded = false
                    if (it == InputMode.Touch && hovered) {
                        skipNextClick = true
                    }
                    true
                },
                modifier = menuModifier,
                style = menuStyle,
                horizontalAlignment = Alignment.Start,
                content = menuContent,
            )
        }
    }
}

@Composable
private fun LinkImpl(
    text: String,
    style: LinkStyle,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    textStyle: TextStyle,
    overflow: TextOverflow,
    interactionSource: MutableInteractionSource,
    icon: IconKey?,
) {
    var linkState by remember(interactionSource, enabled) { mutableStateOf(LinkState.of(enabled = enabled)) }
    remember(enabled) { linkState = linkState.copy(enabled = enabled) }

    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> linkState = linkState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> linkState = linkState.copy(pressed = false)

                is HoverInteraction.Enter -> linkState = linkState.copy(hovered = true)
                is HoverInteraction.Exit -> linkState = linkState.copy(hovered = false)
                is FocusInteraction.Focus -> {
                    if (inputModeManager.inputMode == InputMode.Keyboard) {
                        linkState = linkState.copy(focused = true)
                    }
                }

                is FocusInteraction.Unfocus -> linkState = linkState.copy(focused = false, pressed = false)
            }
        }
    }

    val textColor by style.colors.contentFor(linkState)
    val mergedTextStyle =
        remember(style.underlineBehavior, textStyle, linkState, textColor) {
            val decoration =
                when {
                    style.underlineBehavior == ShowAlways -> TextDecoration.Underline
                    style.underlineBehavior == ShowOnHover && linkState.isHovered -> TextDecoration.Underline
                    else -> TextDecoration.None
                }

            textStyle.merge(textDecoration = decoration, color = textColor)
        }

    val pointerChangeModifier = Modifier.pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))

    Row(
        modifier =
            modifier
                .thenIf(linkState.isEnabled) { pointerChangeModifier }
                .clickable(
                    onClick = {
                        linkState = linkState.copy(visited = true)
                        onClick()
                    },
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = null,
                )
                .focusOutline(linkState, RoundedCornerShape(style.metrics.focusHaloCornerSize)),
        horizontalArrangement = Arrangement.spacedBy(style.metrics.textIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = text, style = mergedTextStyle, overflow = overflow, softWrap = true, maxLines = 1)

        if (icon != null) {
            Icon(
                key = icon,
                contentDescription = null,
                modifier = Modifier.size(style.metrics.iconSize),
                colorFilter = if (!linkState.isEnabled) ColorFilter.disabled() else null,
                hint = Stateful(linkState),
            )
        }
    }
}

@Immutable
@JvmInline
public value class LinkState(public val state: ULong) : FocusableComponentState {
    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    public val isVisited: Boolean
        get() = state and Visited != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override fun toString(): String =
        "${javaClass.simpleName}(enabled=$isEnabled, focused=$isFocused, visited=$isVisited, " +
            "pressed=$isPressed, hovered=$isHovered, isActive=$isActive)"

    public fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        visited: Boolean = isVisited,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): LinkState =
        of(
            enabled = enabled,
            focused = focused,
            visited = visited,
            pressed = pressed,
            hovered = hovered,
            active = active,
        )

    @Composable
    public fun <T> chooseValueWithVisited(
        normal: T,
        disabled: T,
        focused: T,
        pressed: T,
        hovered: T,
        visited: T,
        active: T,
    ): T =
        when {
            !isEnabled -> disabled
            isPressed && !isSwingCompatMode -> pressed
            isHovered && !isSwingCompatMode -> hovered
            isFocused -> focused
            isVisited -> visited
            isActive -> active
            else -> normal
        }

    public companion object {
        private const val VISITED_BIT_OFFSET = CommonStateBitMask.FIRST_AVAILABLE_OFFSET

        private val Visited = 1UL shl VISITED_BIT_OFFSET

        public fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            visited: Boolean = false,
            hovered: Boolean = false,
            pressed: Boolean = false,
            active: Boolean = false,
        ): LinkState =
            LinkState(
                (if (visited) Visited else 0UL) or
                    (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}
