@file:Suppress("MatchingDeclarationName")

package org.jetbrains.jewel.themes.expui.standalone.control

import androidx.compose.foundation.Indication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.themes.expui.standalone.style.AreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.AreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.DisabledAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.FocusAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.HoverAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.style.LocalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDefaultTextStyle
import org.jetbrains.jewel.themes.expui.standalone.style.LocalDisabledAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalFocusAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalHoverAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalNormalAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.LocalPressedAreaColors
import org.jetbrains.jewel.themes.expui.standalone.style.PressedAreaProvider
import org.jetbrains.jewel.themes.expui.standalone.theme.LightTheme
import java.awt.Cursor

class LinkColors(
    override val normalAreaColors: AreaColors,
    val visitedAreaColors: AreaColors,
    override val focusAreaColors: AreaColors,
    override val disabledAreaColors: AreaColors,
    override val hoverAreaColors: AreaColors,
    override val pressedAreaColors: AreaColors
) : AreaProvider, HoverAreaProvider, PressedAreaProvider, DisabledAreaProvider, FocusAreaProvider {

    @Composable
    fun provideArea(
        enabled: Boolean,
        visited: Boolean,
        hover: Boolean,
        pressed: Boolean,
        content: @Composable () -> Unit
    ) {
        val currentColors = when {
            !enabled -> disabledAreaColors
            pressed -> pressedAreaColors
            hover -> hoverAreaColors
            visited -> visitedAreaColors
            else -> normalAreaColors
        }
        CompositionLocalProvider(
            LocalAreaColors provides currentColors,
            LocalNormalAreaColors provides normalAreaColors,
            LocalDisabledAreaColors provides disabledAreaColors,
            LocalPressedAreaColors provides pressedAreaColors,
            LocalHoverAreaColors provides hoverAreaColors,
            LocalFocusAreaColors provides focusAreaColors,
            content = content
        )
    }
}

val LocalLinkColors = compositionLocalOf {
    LightTheme.LinkColors
}

@OptIn(ExperimentalComposeUiApi::class)
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
    lineHeight: TextUnit = TextUnit.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    colors: LinkColors = LocalLinkColors.current,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalDefaultTextStyle.current,
    trailerIcon: @Composable (() -> Unit)? = null
) {
    val visited = remember { mutableStateOf(false) }
    val hovered = remember { mutableStateOf(false) }
    val pressed = remember { mutableStateOf(false) }

    colors.provideArea(enabled, visited.value, hovered.value, pressed.value) {
        val currentAreaColors = LocalAreaColors.current
        val mergedStyle = style.merge(
            TextStyle(
                color = currentAreaColors.text,
                fontSize = fontSize,
                fontWeight = fontWeight,
                textAlign = textAlign,
                lineHeight = lineHeight,
                fontFamily = fontFamily,
                textDecoration = if (hovered.value) {
                    TextDecoration.Underline
                } else {
                    TextDecoration.None
                },
                fontStyle = fontStyle,
                letterSpacing = letterSpacing
            )
        )

        val focus = remember { mutableStateOf(false) }
        Box(
            modifier = Modifier.onFocusEvent {
                focus.value = it.isFocused
            }.focusable(enabled, interactionSource).drawWithCache {
                onDrawBehind {
                    if (focus.value) {
                        val controlOutline = RoundedCornerShape(2.dp).createOutline(size, layoutDirection, this)
                        val highlightOutline =
                            RoundRect(controlOutline.bounds.inflate(2.dp.toPx()), CornerRadius(4.dp.toPx()))
                        val highlightPath = Path().apply {
                            this.fillType = PathFillType.EvenOdd
                            addRoundRect(highlightOutline)
                            addOutline(controlOutline)
                            close()
                        }
                        drawPath(highlightPath, currentAreaColors.focusColor)
                    }
                }
            }.onKeyEvent {
                if (it.type != KeyEventType.KeyUp) return@onKeyEvent false
                if (!focus.value) return@onKeyEvent false
                when (it.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        visited.value = true
                        onClick()
                        return@onKeyEvent true
                    }
                }
                false
            }
        ) {
            val rowInteractionSource = remember { MutableInteractionSource() }
            val focusManager = LocalFocusManager.current
            Row(
                modifier = modifier.focusProperties {
                    this.canFocus = false
                }.clickable(
                    onClick = {
                        visited.value = true
                        if (!focus.value) {
                            focusManager.clearFocus()
                        }
                        onClick()
                    },
                    enabled = enabled,
                    role = Role.Button,
                    indication = indication,
                    interactionSource = rowInteractionSource
                ).pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (!enabled) {
                                return@awaitPointerEventScope
                            }
                            when (event.type) {
                                PointerEventType.Enter -> hovered.value = true
                                PointerEventType.Exit -> hovered.value = false
                                PointerEventType.Press -> pressed.value = true
                                PointerEventType.Release -> pressed.value = false
                                else -> {}
                            }
                        }
                    }
                }.composed {
                    if (enabled) {
                        pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                    } else {
                        this
                    }
                }
            ) {
                BasicText(
                    text = text,
                    style = mergedStyle,
                    onTextLayout = onTextLayout,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    maxLines = 1
                )
                trailerIcon?.invoke()
            }
        }
    }
}

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
    lineHeight: TextUnit = TextUnit.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    colors: LinkColors = LocalLinkColors.current,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalDefaultTextStyle.current
) {
    Link(
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
        lineHeight = lineHeight,
        interactionSource = interactionSource,
        indication = indication,
        colors = colors,
        onTextLayout = onTextLayout,
        style = style
    ) {
        Icon("icons/external_link_arrow.svg")
    }
}

@Composable
fun DropdownLink(
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
    lineHeight: TextUnit = TextUnit.Unspecified,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    colors: LinkColors = LocalLinkColors.current,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalDefaultTextStyle.current
) {
    Link(
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
        lineHeight = lineHeight,
        interactionSource = interactionSource,
        indication = indication,
        colors = colors,
        onTextLayout = onTextLayout,
        style = style
    ) {
        Icon("icons/linkDropTriangle.svg")
    }
}
