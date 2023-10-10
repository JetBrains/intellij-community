package org.jetbrains.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.ResourceLoader
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.CommonStateBitMask.Active
import org.jetbrains.jewel.CommonStateBitMask.Enabled
import org.jetbrains.jewel.CommonStateBitMask.Focused
import org.jetbrains.jewel.CommonStateBitMask.Hovered
import org.jetbrains.jewel.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border
import org.jetbrains.jewel.styling.DropdownStyle
import org.jetbrains.jewel.styling.LocalMenuStyle
import org.jetbrains.jewel.styling.MenuStyle
import org.jetbrains.jewel.util.appendIf

@Composable
fun Dropdown(
    resourceLoader: ResourceLoader,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuModifier: Modifier = Modifier,
    outline: Outline = Outline.None,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    style: DropdownStyle = IntelliJTheme.dropdownStyle,
    menuContent: MenuScope.() -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Box {
        var expanded by remember { mutableStateOf(false) }
        var skipNextClick by remember { mutableStateOf(false) }

        var dropdownState by remember(interactionSource) {
            mutableStateOf(DropdownState.of(enabled = enabled))
        }

        remember(enabled) {
            dropdownState = dropdownState.copy(enabled = enabled)
        }

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> dropdownState = dropdownState.copy(pressed = true)
                    is PressInteraction.Cancel, is PressInteraction.Release ->
                        dropdownState =
                            dropdownState.copy(pressed = false)

                    is HoverInteraction.Enter -> dropdownState = dropdownState.copy(hovered = true)
                    is HoverInteraction.Exit -> dropdownState = dropdownState.copy(hovered = false)
                    is FocusInteraction.Focus -> dropdownState = dropdownState.copy(focused = true)
                    is FocusInteraction.Unfocus -> dropdownState = dropdownState.copy(focused = false)
                }
            }
        }

        val colors = style.colors
        val metrics = style.metrics
        val shape = RoundedCornerShape(style.metrics.cornerSize)
        val minSize = metrics.minSize
        val arrowMinSize = style.metrics.arrowMinSize
        val borderColor by colors.borderFor(dropdownState)

        val outlineState = remember(dropdownState, expanded) {
            dropdownState.copy(focused = dropdownState.isFocused || expanded)
        }

        Box(
            modifier.clickable(
                onClick = {
                    // TODO: Trick to skip click event when close menu by click dropdown
                    if (!skipNextClick) {
                        expanded = !expanded
                    }
                    skipNextClick = false
                },
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
            )
                .background(colors.backgroundFor(dropdownState).value, shape)
                .border(Stroke.Alignment.Center, style.metrics.borderWidth, borderColor, shape)
                .appendIf(outline == Outline.None) { focusOutline(outlineState, shape) }
                .outline(outlineState, outline, shape)
                .defaultMinSize(minSize.width, minSize.height.coerceAtLeast(arrowMinSize.height)),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.contentFor(dropdownState).value,
            ) {
                Row(
                    modifier = Modifier.padding(style.metrics.contentPadding)
                        .padding(end = minSize.height),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        content()
                    },
                )

                Box(
                    modifier = Modifier.size(arrowMinSize)
                        .align(Alignment.CenterEnd),
                    contentAlignment = Alignment.Center,
                ) {
                    val chevronIcon by style.icons.chevronDown.getPainter(resourceLoader, dropdownState)
                    Icon(
                        painter = chevronIcon,
                        contentDescription = null,
                        tint = colors.iconTintFor(dropdownState).value,
                    )
                }
            }
        }

        if (expanded) {
            DropdownMenu(
                onDismissRequest = {
                    expanded = false
                    if (it == InputMode.Touch && dropdownState.isHovered) {
                        skipNextClick = true
                    }
                    true
                },
                modifier = menuModifier,
                style = style.menuStyle,
                horizontalAlignment = Alignment.Start,
                content = menuContent,
                resourceLoader = resourceLoader,
            )
        }
    }
}

@Composable
internal fun DropdownMenu(
    onDismissRequest: (InputMode) -> Boolean,
    horizontalAlignment: Alignment.Horizontal,
    resourceLoader: ResourceLoader,
    modifier: Modifier = Modifier,
    style: MenuStyle,
    content: MenuScope.() -> Unit,
) {
    val density = LocalDensity.current

    val popupPositionProvider = AnchorVerticalMenuPositionProvider(
        contentOffset = style.metrics.offset,
        contentMargin = style.metrics.menuMargin,
        alignment = horizontalAlignment,
        density = density,
    )

    var focusManager: FocusManager? by remember { mutableStateOf(null) }
    var inputModeManager: InputModeManager? by remember { mutableStateOf(null) }
    val menuManager = remember(onDismissRequest) {
        MenuManager(onDismissRequest = onDismissRequest)
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = { onDismissRequest(InputMode.Touch) },
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { false },
        onKeyEvent = {
            val currentFocusManager = checkNotNull(focusManager) { "FocusManager must not be null" }
            val currentInputModeManager = checkNotNull(inputModeManager) { "InputModeManager must not be null" }
            handlePopupMenuOnKeyEvent(it, currentFocusManager, currentInputModeManager, menuManager)
        },
    ) {
        focusManager = LocalFocusManager.current
        inputModeManager = LocalInputModeManager.current

        CompositionLocalProvider(
            LocalMenuManager provides menuManager,
            LocalMenuStyle provides style,
        ) {
            MenuContent(
                modifier = modifier,
                content = content,
                resourceLoader = resourceLoader,
            )
        }
    }
}

@Immutable
@JvmInline
value class DropdownState(val state: ULong) : FocusableComponentState {

    @Stable
    override val isActive: Boolean
        get() = state and Active != 0UL

    @Stable
    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    @Stable
    override val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    @Stable
    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ) = of(
        enabled = enabled,
        focused = focused,
        pressed = pressed,
        hovered = hovered,
        active = active,
    )

    override fun toString() =
        "${javaClass.simpleName}(isEnabled=$isEnabled, isFocused=$isFocused, " +
            "isHovered=$isHovered, isPressed=$isPressed, isActive=$isActive)"

    companion object {

        fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ) = DropdownState(
            (if (enabled) Enabled else 0UL) or
                (if (focused) Focused else 0UL) or
                (if (hovered) Hovered else 0UL) or
                (if (pressed) Pressed else 0UL) or
                (if (active) Active else 0UL),
        )
    }
}
