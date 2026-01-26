package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Focused
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.foundation.theme.LocalTextStyle
import org.jetbrains.jewel.ui.component.styling.BadgeStyle
import org.jetbrains.jewel.ui.focusOutline
import org.jetbrains.jewel.ui.theme.badgeStyle

/**
 * A small, colored badge component used to display promotional content like "New", "Beta", or other short labels.
 *
 * Badges are compact UI elements with customizable backgrounds and text colors, typically used to highlight features,
 * statuses, or promotional messages. They can be either static (non-clickable) or interactive (clickable).
 *
 * By default, badges have square corners. To add rounded corners, provide a custom [BadgeStyle] with modified metrics:
 * ```kotlin
 * Badge {
 *     Text("New")
 * }
 * ```
 *
 * **Usage example:**
 * [`Badges.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Badges.kt)
 *
 * @param modifier [Modifier] to be applied to the badge.
 * @param onClick Optional click handler. When provided, the badge becomes clickable. When null, the badge is static.
 * @param enabled Controls the enabled state of the badge. When false, the badge appears dimmed and is not clickable.
 * @param interactionSource An optional [MutableInteractionSource] for observing and emitting interactions for this
 *   badge. Use this to observe state changes or customize interaction handling.
 * @param style The visual styling configuration for the badge. Defaults to the theme's badge style.
 * @param textStyle The typography style to be applied to the badge's text content.
 * @param content The content to be displayed inside the badge.
 */
@Composable
public fun Badge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: BadgeStyle = JewelTheme.badgeStyle.default,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable RowScope.() -> Unit,
) {
    BadgeImpl(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        style = style,
        textStyle = textStyle,
        content = content,
    )
}

@Composable
internal fun BadgeImpl(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: BadgeStyle = JewelTheme.badgeStyle.default,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = style.colors
    val metrics = style.metrics
    val shape = RoundedCornerShape(metrics.cornerSize)

    var badgeState by remember(interactionSource, enabled) { mutableStateOf(BadgeState.of(enabled = enabled)) }

    remember(enabled) { badgeState = badgeState.copy(enabled = enabled) }

    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(interactionSource, enabled, inputModeManager) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> badgeState = badgeState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> badgeState = badgeState.copy(pressed = false)
                is HoverInteraction.Enter -> badgeState = badgeState.copy(hovered = true)
                is HoverInteraction.Exit -> badgeState = badgeState.copy(hovered = false)
                is FocusInteraction.Focus -> {
                    if (inputModeManager.inputMode == InputMode.Keyboard) {
                        badgeState = badgeState.copy(focused = true)
                    }
                }
                is FocusInteraction.Unfocus -> badgeState = badgeState.copy(focused = false)
            }
        }
    }

    val backgroundColor by colors.backgroundFor(badgeState)
    val contentColor by colors.contentFor(badgeState)

    Row(
        modifier =
            modifier
                .thenIf(onClick != null) {
                    clickable(
                            onClick = onClick ?: {},
                            enabled = enabled,
                            role = Role.Button,
                            interactionSource = interactionSource,
                            indication = null,
                        )
                        .thenIf(enabled) { pointerHoverIcon(PointerIcon.Hand) }
                }
                .height(metrics.height)
                .background(backgroundColor, shape)
                .focusOutline(badgeState, shape)
                .padding(metrics.padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides textStyle.copy(color = contentColor.takeOrElse { textStyle.color }),
        ) {
            content()
        }
    }
}

/**
 * Represents the visual state of a [Badge](org.jetbrains.jewel.ui.component.Badge).
 *
 * This is an immutable state object that tracks whether the badge is enabled, focused, hovered, pressed, and active.
 *
 * @property state The underlying state value as a ULong bit mask.
 */
@Immutable
@JvmInline
public value class BadgeState(public val state: ULong) : FocusableComponentState {
    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isFocused: Boolean
        get() = state and Focused != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        hovered: Boolean = isHovered,
        pressed: Boolean = isPressed,
        active: Boolean = isActive,
    ): BadgeState = of(enabled = enabled, focused = focused, hovered = hovered, pressed = pressed, active = active)

    override fun toString(): String =
        "BadgeState(isEnabled=$isEnabled, isFocused=$isFocused, isHovered=$isHovered, " +
            "isPressed=$isPressed, isActive=$isActive)"

    public companion object {
        public fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            hovered: Boolean = false,
            pressed: Boolean = false,
            active: Boolean = false,
        ): BadgeState =
            BadgeState(
                (if (enabled) Enabled else 0UL) or
                    (if (focused) Focused else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}
