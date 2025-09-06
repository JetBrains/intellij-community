// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onFirstVisible
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberComponentRectPositionProvider
import java.awt.event.KeyEvent as AWTKeyEvent
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.SearchMatchStyle
import org.jetbrains.jewel.ui.component.styling.SpeedSearchStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.theme.searchMatchStyle
import org.jetbrains.jewel.ui.theme.speedSearchStyle
import org.jetbrains.jewel.ui.theme.textFieldStyle
import org.jetbrains.skiko.hostOs

@Composable
internal fun SpeedSearchArea(
    state: SpeedSearchState,
    modifier: Modifier = Modifier,
    styling: SpeedSearchStyle = JewelTheme.speedSearchStyle,
    textFieldStyle: TextFieldStyle = JewelTheme.textFieldStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    searchMatchStyle: SearchMatchStyle = JewelTheme.searchMatchStyle,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable SpeedSearchScope.() -> Unit,
) {
    val finalInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    Box(modifier = modifier) {
        val scope = remember(this, state) { SpeedSearchScopeImpl(this, state, finalInteractionSource) }

        scope.content()

        if (state.isVisible) {
            SpeedSearchInput(
                state.textFieldState,
                state.hasMatches,
                styling,
                textStyle,
                textFieldStyle,
                finalInteractionSource,
            )
        }
    }
}

internal abstract class SpeedSearchState {
    val textFieldState = TextFieldState()

    var isVisible: Boolean by mutableStateOf(false)
    val searchText: String by derivedStateOf { textFieldState.text.toString() }

    abstract val hasMatches: Boolean

    abstract fun matchingParts(text: String?): List<IntRange>?
}

internal interface SpeedSearchScope : BoxScope {
    val speedSearchState: SpeedSearchState
    val interactionSource: MutableInteractionSource

    fun processKeyEvent(event: KeyEvent): Boolean
}

public interface SpeedSearchItemScope {
    public val matches: List<IntRange>?

    public fun Modifier.highlightTextSearchArea(textLayoutResult: TextLayoutResult?): Modifier

    public fun CharSequence.highlightTextSearch(): AnnotatedString
}

@Composable
private fun SpeedSearchInput(
    state: TextFieldState,
    hasMatch: Boolean,
    styling: SpeedSearchStyle,
    textStyle: TextStyle,
    textFieldStyle: TextFieldStyle,
    interactionSource: MutableInteractionSource,
) {
    val foregroundColor = styling.getCurrentForegroundColor(hasMatch, textFieldStyle, textStyle)

    Popup(popupPositionProvider = rememberComponentRectPositionProvider(Alignment.TopStart, Alignment.TopEnd)) {
        val focusRequester = remember { FocusRequester() }

        BasicTextField(
            state = state,
            cursorBrush = SolidColor(foregroundColor),
            textStyle = textStyle.merge(TextStyle(color = foregroundColor)),
            interactionSource = interactionSource,
            modifier = Modifier.focusRequester(focusRequester).onFirstVisible { focusRequester.requestFocus() },
            decorator = { innerTextField ->
                Row(
                    modifier =
                        Modifier.background(styling.colors.background)
                            .border(1.dp, styling.colors.border)
                            .padding(styling.metrics.contentPadding)
                ) {
                    Icon(
                        key = styling.icons.magnifyingGlass,
                        contentDescription = null,
                        tint = styling.colors.foreground,
                        modifier = Modifier.padding(end = 10.dp),
                    )

                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.text.isEmpty()) {
                            Text(
                                text = "Search",
                                style = textStyle.merge(TextStyle(color = textFieldStyle.colors.placeholder)),
                            )
                        }

                        innerTextField()
                    }
                }
            },
        )
    }
}

private class SpeedSearchScopeImpl(
    val delegate: BoxScope,
    override val speedSearchState: SpeedSearchState,
    override val interactionSource: MutableInteractionSource,
) : SpeedSearchScope, BoxScope by delegate {
    /** @see com.intellij.ui.SpeedSearchBase.isNavigationKey function */
    private val invalidNavigationKeys =
        setOf(
            AWTKeyEvent.VK_HOME,
            AWTKeyEvent.VK_END,
            AWTKeyEvent.VK_UP,
            AWTKeyEvent.VK_DOWN,
            AWTKeyEvent.VK_PAGE_UP,
            AWTKeyEvent.VK_PAGE_DOWN,
        )

    private val validKeyEvent = setOf(AWTKeyEvent.VK_LEFT, AWTKeyEvent.VK_RIGHT)

    override fun processKeyEvent(event: KeyEvent): Boolean {
        val textFieldState = speedSearchState.textFieldState

        return when {
            event.type != KeyEventType.KeyDown -> false // Only handle key down events
            event.isMetaPressed -> false // Ignore meta key events
            (event.isShiftPressed || event.isAltPressed) && event.key.nativeKeyCode in invalidNavigationKeys -> false
            textFieldState.text.isNotEmpty() && event.key.nativeKeyCode in validKeyEvent ->
                textFieldState.handleTextNavigationKeys(event)
            event.key == Key.Escape -> hideSpeedSearch()
            event.key == Key.Delete -> textFieldState.handleDeleteKeyInput(event)
            event.key == Key.Backspace -> textFieldState.handleBackspaceKeyInput(event)
            event.awtEventOrNull?.isReallyTypedEvent() != true -> false // Only handle printable keys
            else -> textFieldState.handleValidKeyInput(event)
        }
    }

    private fun hideSpeedSearch(): Boolean {
        if (!clearSearchInput()) return false
        speedSearchState.isVisible = false
        return true
    }

    private fun clearSearchInput(): Boolean {
        if (!speedSearchState.isVisible) return false
        speedSearchState.textFieldState.edit { delete(0, length) }
        return true
    }

    private fun TextFieldState.handleTextNavigationKeys(event: KeyEvent): Boolean =
        when (event.key.nativeKeyCode) {
            AWTKeyEvent.VK_RIGHT ->
                if (event.isAltPressed) {
                    edit { placeCursorAtEnd() }
                    true
                } else {
                    hideSpeedSearch()
                    false
                }
            AWTKeyEvent.VK_LEFT ->
                if (event.isAltPressed) {
                    edit { placeCursorBeforeCharAt(0) }
                    true
                } else {
                    hideSpeedSearch()
                    false
                }
            else -> false
        }

    private fun TextFieldState.handleDeleteKeyInput(event: KeyEvent): Boolean =
        when {
            text.isEmpty() -> false
            event.isAltPressed -> clearSearchInput()
            selection.end < text.length -> false
            else -> {
                edit {
                    if (selection.start != selection.end) {
                        delete(selection.min, selection.max)
                    } else if (selection.end < length) {
                        delete(selection.end, selection.end + 1)
                    }
                }

                true
            }
        }

    private fun TextFieldState.handleBackspaceKeyInput(event: KeyEvent): Boolean =
        when {
            text.isEmpty() -> false
            event.isAltPressed -> clearSearchInput()
            selection.start <= 0 -> false
            else -> {
                edit {
                    if (selection.start != selection.end) {
                        delete(selection.min, selection.max)
                    } else if (selection.end > 0) {
                        delete(selection.start - 1, selection.start)
                    }
                }

                true
            }
        }

    private fun TextFieldState.handleValidKeyInput(event: KeyEvent): Boolean {
        val char = event.awtEventOrNull?.keyChar ?: return false

        if (char.isWhitespace() && text.isBlank() || PUNCTUATION_MARKS.contains(char)) {
            return false
        }

        edit {
            if (selection.start != selection.end) {
                replace(selection.min, selection.max, char.toString())
            } else {
                append(char.toString())
            }
        }

        if (!speedSearchState.isVisible && text.isNotEmpty()) {
            speedSearchState.isVisible = true
        }

        return true
    }
}

private fun SpeedSearchStyle.getCurrentForegroundColor(
    hasMatch: Boolean,
    textFieldStyle: TextFieldStyle,
    textStyle: TextStyle,
): Color {
    if (!hasMatch && colors.error.isSpecified) return colors.error
    return colors.foreground.takeOrElse { textFieldStyle.colors.content }.takeOrElse { textStyle.color }
}

/**
 * **Swing Version:**
 * [UIUtil.isReallyTypedEvent](https://github.com/JetBrains/intellij-community/blob/master/platform/util/ui/src/com/intellij/util/ui/UIUtil.java)
 */
private fun AWTKeyEvent.isReallyTypedEvent(): Boolean {
    val keyChar = keyChar
    val code = keyChar.code

    return when {
        // Ignoring undefined characters
        keyChar == AWTKeyEvent.CHAR_UNDEFINED -> {
            false
        }

        // Handling non-printable chars (e.g. Tab, Enter, Delete, etc.)
        keyChar.code < 0x20 || keyChar.code == 0x7F -> {
            false
        }

        // Allow input of special characters on Windows in Persian keyboard layout using Ctrl+Shift+1..4
        hostOs.isWindows && code >= 0x200C && code <= 0x200D -> {
            true
        }
        hostOs.isMacOS -> {
            !isMetaDown && !isControlDown
        }
        else -> {
            !isAltDown && !isControlDown
        }
    }
}

/**
 * **Swing Version:**
 * [SpeedSearch.PUNCTUATION_MARKS](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/speedSearch/SpeedSearch.java)
 */
private const val PUNCTUATION_MARKS = "*_-+\"'/.#$>:,;?!@%^&"
