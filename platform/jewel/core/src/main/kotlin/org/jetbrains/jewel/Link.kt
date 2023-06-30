package org.jetbrains.jewel

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import org.jetbrains.jewel.foundation.Stroke
import org.jetbrains.jewel.foundation.border
import org.jetbrains.jewel.foundation.onHover

@Composable
fun Link(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    lineHeight: TextUnit = TextUnit.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    defaults: LinkDefaults = IntelliJTheme.linkDefaults,
    colors: LinkColors = defaults.colors()
) = LinkImpl(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    fontSize = fontSize,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textAlign = textAlign,
    overflow = overflow,
    lineHeight = lineHeight,
    interactionSource = interactionSource,
    indication = indication,
    defaults = defaults,
    colors = colors
)

@Composable
fun ExternalLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    lineHeight: TextUnit = TextUnit.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    defaults: LinkDefaults = IntelliJTheme.linkDefaults,
    colors: LinkColors = defaults.colors()
) = LinkImpl(
    text = text,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    fontSize = fontSize,
    fontStyle = fontStyle,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    letterSpacing = letterSpacing,
    textAlign = textAlign,
    overflow = overflow,
    lineHeight = lineHeight,
    interactionSource = interactionSource,
    indication = indication,
    defaults = defaults,
    colors = colors
) {
    Icon(
        painter = defaults.externalLinkIconPainter(),
        tint = colors.iconColor(it).value
    )
}

@Composable
fun DropdownLink(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    lineHeight: TextUnit = TextUnit.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    defaults: LinkDefaults = IntelliJTheme.linkDefaults,
    colors: LinkColors = defaults.colors(),
    menuModifier: Modifier = Modifier,
    menuDefaults: MenuDefaults = IntelliJTheme.dropdownDefaults,
    menuContent: MenuScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    var skipNextClick by remember { mutableStateOf(false) }
    Box(
        Modifier.onHover {
            hovered = it
        }
    ) {
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
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textAlign = textAlign,
            overflow = overflow,
            lineHeight = lineHeight,
            interactionSource = interactionSource,
            indication = indication,
            defaults = defaults,
            colors = colors
        ) {
            Icon(
                painter = defaults.DropdownLinkIconPainter(),
                tint = colors.iconColor(it).value
            )
        }

        if (expanded) {
            DropdownMenu(
                onDismissRequest = {
                    expanded = false
                    if (it == InputMode.Touch && hovered) {
                        skipNextClick = true
                    }
                    true
                },
                modifier = menuModifier,
                defaults = menuDefaults,
                content = menuContent
            )
        }
    }
}

@Composable
private fun LinkImpl(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    lineHeight: TextUnit = TextUnit.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    defaults: LinkDefaults = IntelliJTheme.linkDefaults,
    colors: LinkColors = defaults.colors(),
    icon: (@Composable RowScope.(state: LinkState) -> Unit)? = null
) {
    var linkState by remember(interactionSource) {
        mutableStateOf(LinkState.of(enabled = enabled))
    }
    remember(enabled) {
        linkState = linkState.copy(enabled = enabled)
    }

    val inputModeManager = LocalInputModeManager.current
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> linkState = linkState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release -> linkState = linkState.copy(pressed = false)
                is HoverInteraction.Enter -> linkState = linkState.copy(hovered = true)
                is HoverInteraction.Exit -> linkState = linkState.copy(hovered = false)

                is FocusInteraction.Focus -> {
                    if (inputModeManager.inputMode == InputMode.Keyboard) {
                        linkState = linkState.copy(focused = true)
                    }
                }

                is FocusInteraction.Unfocus -> {
                    linkState = linkState.copy(focused = false, pressed = false)
                }
            }
        }
    }

    val textColor = colors.textColor(linkState).value

    val mergedStyle = defaults.textStyle(linkState).value.merge(
        TextStyle(
            color = textColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            lineHeight = lineHeight,
            fontFamily = fontFamily,
            fontStyle = fontStyle,
            letterSpacing = letterSpacing
        )
    )

    val clickable = Modifier.clickable(
        onClick = {
            linkState = linkState.copy(visited = true)
            onClick()
        },
        enabled = enabled,
        role = Role.Button,
        interactionSource = interactionSource,
        indication = indication
    )

    if (icon == null) {
        BasicText(
            text = text,
            modifier = modifier.then(clickable)
                .border(colors.haloStroke(linkState).value, defaults.haloShape()),
            style = mergedStyle,
            overflow = overflow,
            softWrap = true,
            maxLines = 1
        )
    } else {
        Row(
            modifier = modifier.then(clickable)
                .border(colors.haloStroke(linkState).value, defaults.haloShape())
        ) {
            BasicText(
                text = text,
                style = mergedStyle,
                overflow = overflow,
                softWrap = true,
                maxLines = 1
            )
            icon(linkState)
        }
    }
}

@Immutable
@JvmInline
value class LinkState(val state: ULong) {

    @Stable
    val isEnabled: Boolean
        get() = state and Enabled != 0UL

    @Stable
    val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    val isVisited: Boolean
        get() = state and Visited != 0UL

    @Stable
    val isPressed: Boolean
        get() = state and Pressed != 0UL

    @Stable
    val isHovered: Boolean
        get() = state and Hovered != 0UL

    override fun toString(): String = "LinkState(enabled=$isEnabled, focused=$isFocused, visited=$isVisited, pressed=$isPressed, hovered=$isHovered)"

    fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        visited: Boolean = isVisited,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered
    ) = of(
        enabled = enabled,
        focused = focused,
        visited = visited,
        pressed = pressed,
        hovered = hovered
    )

    companion object {

        private val Enabled = 1UL shl 0
        private val Focused = 1UL shl 1
        private val Visited = 1UL shl 2
        private val Hovered = 1UL shl 3
        private val Pressed = 1UL shl 4

        fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            visited: Boolean = false,
            hovered: Boolean = false,
            pressed: Boolean = false
        ): LinkState {
            var state = 0UL
            if (enabled) state = state or Enabled
            if (focused) state = state or Focused
            if (visited) state = state or Visited
            if (hovered) state = state or Hovered
            if (pressed) state = state or Pressed
            return LinkState(state)
        }
    }
}

@Stable
interface LinkColors {

    @Composable
    fun textColor(state: LinkState): State<Color>

    @Composable
    fun iconColor(state: LinkState): State<Color>

    @Composable
    fun haloStroke(state: LinkState): State<Stroke>
}

@Stable
interface LinkDefaults {

    @Composable
    fun haloShape(): Shape

    @Composable
    fun externalLinkIconPainter(): Painter

    @Composable
    fun DropdownLinkIconPainter(): Painter

    @Composable
    fun colors(): LinkColors

    @Composable
    fun textStyle(state: LinkState): State<TextStyle>
}

fun linkColors(
    textColor: Color,
    visitedTextColor: Color,
    disabledTextColor: Color,
    iconColor: Color,
    disabledIconColor: Color,
    haloStroke: Stroke
): LinkColors = DefaultLinkColors(
    textColor = textColor,
    visitedTextColor = visitedTextColor,
    disabledTextColor = disabledTextColor,
    iconColor = iconColor,
    disabledIconColor = disabledIconColor,
    haloStroke = haloStroke
)

@Immutable
private data class DefaultLinkColors(
    val textColor: Color,
    val visitedTextColor: Color,
    val disabledTextColor: Color,
    val iconColor: Color,
    val disabledIconColor: Color,
    val haloStroke: Stroke
) : LinkColors {

    @Composable
    override fun textColor(state: LinkState): State<Color> = rememberUpdatedState(
        when {
            !state.isEnabled -> disabledTextColor
            state.isVisited -> visitedTextColor
            else -> textColor
        }
    )

    @Composable
    override fun iconColor(state: LinkState): State<Color> = rememberUpdatedState(
        when {
            !state.isEnabled -> disabledIconColor
            else -> iconColor
        }
    )

    @Composable
    override fun haloStroke(state: LinkState): State<Stroke> = rememberUpdatedState(
        when {
            !state.isEnabled -> Stroke.None
            state.isFocused -> haloStroke
            else -> Stroke.None
        }
    )
}

internal val LocalLinkDefaults = compositionLocalOf<LinkDefaults> {
    error("No LinkDefaults provided")
}
