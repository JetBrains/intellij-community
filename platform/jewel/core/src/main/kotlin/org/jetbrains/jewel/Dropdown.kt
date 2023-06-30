package org.jetbrains.jewel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border

@Composable
fun Dropdown(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuModifier: Modifier = Modifier,
    error: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    defaults: DropdownDefaults = IntelliJTheme.dropdownDefaults,
    colors: DropdownColors = defaults.colors(),
    menuContent: MenuScope.() -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    Box {
        var expanded by remember { mutableStateOf(false) }
        var skipNextClick by remember { mutableStateOf(false) }

        var dropdownState by remember(interactionSource) {
            mutableStateOf(DropdownState.of(enabled = enabled, error = error))
        }

        remember(enabled, error) {
            dropdownState = dropdownState.copy(enabled = enabled, error = error)
        }

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> dropdownState = dropdownState.copy(pressed = true)
                    is PressInteraction.Cancel, is PressInteraction.Release -> dropdownState = dropdownState.copy(pressed = false)
                    is HoverInteraction.Enter -> dropdownState = dropdownState.copy(hovered = true)
                    is HoverInteraction.Exit -> dropdownState = dropdownState.copy(hovered = false)
                    is FocusInteraction.Focus -> dropdownState = dropdownState.copy(focused = true)
                    is FocusInteraction.Unfocus -> dropdownState = dropdownState.copy(focused = false)
                }
            }
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
                indication = null
            ).background(colors.background(dropdownState).value, defaults.shape())
                .border(colors.borderStroke(dropdownState).value, defaults.shape())
                .defaultMinSize(defaults.minWidth(), defaults.minHeight()),
            contentAlignment = Alignment.CenterStart
        ) {
            CompositionLocalProvider(
                LocalTextColor provides colors.foreground(dropdownState).value
            ) {
                Row(
                    Modifier.padding(defaults.contentPadding()).padding(end = defaults.minHeight()),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        content()
                    }
                )

                Box(
                    modifier = Modifier.size(defaults.minHeight()).align(Alignment.CenterEnd),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = defaults.chevronPainter(),
                        contentDescription = null,
                        tint = colors.foreground(dropdownState).value
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
                defaults = defaults,
                content = menuContent
            )
        }
    }
}

@Composable
internal fun DropdownMenu(
    onDismissRequest: (InputMode) -> Boolean,
    modifier: Modifier = Modifier,
    defaults: MenuDefaults = IntelliJTheme.dropdownDefaults,
    offset: DpOffset = defaults.menuOffset(),
    content: MenuScope.() -> Unit
) {
    val density = LocalDensity.current

    val popupPositionProvider = AnchorVerticalMenuPositionProvider(
        offset,
        defaults.menuMargin(),
        defaults.menuAlignment(),
        density
    )

    var focusManager: FocusManager? by mutableStateOf(null)
    var inputModeManager: InputModeManager? by mutableStateOf(null)
    val menuManager = remember(onDismissRequest) {
        MenuManager(
            onDismissRequest = onDismissRequest
        )
    }

    Popup(
        onDismissRequest = {
            onDismissRequest(InputMode.Touch)
        },
        popupPositionProvider = popupPositionProvider,
        onKeyEvent = {
            handlePopupMenuOnKeyEvent(it, focusManager!!, inputModeManager!!, menuManager)
        },
        focusable = true
    ) {
        focusManager = LocalFocusManager.current
        inputModeManager = LocalInputModeManager.current

        CompositionLocalProvider(
            LocalMenuManager provides menuManager,
            LocalMenuDefaults provides defaults
        ) {
            MenuContent(
                modifier = modifier,
                content = content
            )
        }
    }
}

@Immutable
@JvmInline
value class DropdownState(val state: ULong) {

    @Stable
    val isEnabled: Boolean
        get() = state and Enabled != 0UL

    @Stable
    val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    val isError: Boolean
        get() = state and Error != 0UL

    @Stable
    val isHovered: Boolean
        get() = state and Hovered != 0UL

    @Stable
    val isPressed: Boolean
        get() = state and Pressed != 0UL

    fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        error: Boolean = isError,
        hovered: Boolean = isHovered,
        pressed: Boolean = isPressed
    ): DropdownState = of(
        enabled = enabled,
        focused = focused,
        error = error,
        hovered = hovered,
        pressed = pressed
    )

    override fun toString(): String = "DropdownState(enabled=$isEnabled, focused=$isFocused, error=$isError, hovered=$isHovered, pressed=$isPressed)"

    companion object {

        private val Enabled = 1UL shl 0
        private val Focused = 1UL shl 1
        private val Error = 1UL shl 2
        private val Hovered = 1UL shl 3
        private val Pressed = 1UL shl 4

        fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            error: Boolean = false,
            hovered: Boolean = false,
            pressed: Boolean = false
        ): DropdownState {
            var state = 0UL
            if (enabled) state = state or Enabled
            if (focused) state = state or Focused
            if (error) state = state or Error
            if (hovered) state = state or Hovered
            if (pressed) state = state or Pressed
            return DropdownState(state)
        }
    }
}

@Stable
interface DropdownColors {

    @Composable
    fun foreground(state: DropdownState): State<Color>

    @Composable
    fun background(state: DropdownState): State<Color>

    @Composable
    fun borderStroke(state: DropdownState): State<Stroke>

    @Composable
    fun iconColor(state: DropdownState): State<Color>
}

@Stable
interface DropdownDefaults : MenuDefaults {

    @Composable
    fun colors(): DropdownColors

    @Composable
    fun shape(): Shape

    @Composable
    fun textStyle(): TextStyle

    @Composable
    fun contentPadding(): PaddingValues

    @Composable
    fun minWidth(): Dp

    @Composable
    fun minHeight(): Dp

    @Composable
    fun chevronPainter(): Painter
}

fun dropdownColors(
    foreground: Color,
    background: Color,
    iconColor: Color,
    borderStroke: Stroke,
    focusedForeground: Color,
    focusedBackground: Color,
    focusedBorderStroke: Stroke,
    errorForeground: Color,
    errorBackground: Color,
    errorBorderStroke: Stroke,
    errorFocusedForeground: Color,
    errorFocusedBackground: Color,
    errorFocusedBorderStroke: Stroke,
    disabledForeground: Color,
    disabledBackground: Color,
    disabledBorderStroke: Stroke,
    disabledIconColor: Color
): DropdownColors = DefaultDropdownColors(
    foreground = foreground,
    background = background,
    iconColor = iconColor,
    borderStroke = borderStroke,
    focusedForeground = focusedForeground,
    focusedBackground = focusedBackground,
    focusedBorderStroke = focusedBorderStroke,
    errorForeground = errorForeground,
    errorBackground = errorBackground,
    errorBorderStroke = errorBorderStroke,
    errorFocusedForeground = errorFocusedForeground,
    errorFocusedBackground = errorFocusedBackground,
    errorFocusedBorderStroke = errorFocusedBorderStroke,
    disabledForeground = disabledForeground,
    disabledBackground = disabledBackground,
    disabledBorderStroke = disabledBorderStroke,
    disabledIconColor = disabledIconColor
)

private class DefaultDropdownColors(
    private val foreground: Color,
    private val background: Color,
    private val iconColor: Color,
    private val borderStroke: Stroke,
    private val focusedForeground: Color,
    private val focusedBackground: Color,
    private val focusedBorderStroke: Stroke,
    private val errorForeground: Color,
    private val errorBackground: Color,
    private val errorBorderStroke: Stroke,
    private val errorFocusedForeground: Color,
    private val errorFocusedBackground: Color,
    private val errorFocusedBorderStroke: Stroke,
    private val disabledForeground: Color,
    private val disabledBackground: Color,
    private val disabledBorderStroke: Stroke,
    private val disabledIconColor: Color
) : DropdownColors {

    @Composable
    override fun foreground(state: DropdownState): State<Color> = rememberUpdatedState(
        when {
            !state.isEnabled -> disabledForeground
            state.isError && state.isFocused -> errorFocusedForeground
            state.isError -> errorForeground
            state.isFocused -> focusedForeground
            else -> foreground
        }
    )

    @Composable
    override fun background(state: DropdownState): State<Color> = rememberUpdatedState(
        when {
            !state.isEnabled -> disabledBackground
            state.isError && state.isFocused -> errorFocusedBackground
            state.isError -> errorBackground
            state.isFocused -> focusedBackground
            else -> background
        }
    )

    @Composable
    override fun borderStroke(state: DropdownState): State<Stroke> = rememberUpdatedState(
        when {
            !state.isEnabled -> disabledBorderStroke
            state.isError && state.isFocused -> errorFocusedBorderStroke
            state.isError -> errorBorderStroke
            state.isFocused -> focusedBorderStroke
            else -> borderStroke
        }
    )

    @Composable
    override fun iconColor(state: DropdownState): State<Color> = rememberUpdatedState(
        when {
            !state.isEnabled -> disabledIconColor
            else -> iconColor
        }
    )
}

internal val MenuVerticalMargin = 0.dp

internal val LocalDropdownDefaults = staticCompositionLocalOf<DropdownDefaults> {
    error("No DropdownDefaults provided")
}
