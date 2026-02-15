// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.onFirstVisible
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberComponentRectPositionProvider
import java.awt.event.KeyEvent as AWTKeyEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.SearchMatchStyle
import org.jetbrains.jewel.ui.component.styling.SpeedSearchStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.theme.searchMatchStyle
import org.jetbrains.jewel.ui.theme.speedSearchStyle
import org.jetbrains.jewel.ui.theme.textFieldStyle
import org.jetbrains.skiko.hostOs

@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun SpeedSearchArea(
    modifier: Modifier = Modifier,
    matcherBuilder: (String) -> SpeedSearchMatcher = SpeedSearchMatcher::patternMatcher,
    styling: SpeedSearchStyle = JewelTheme.speedSearchStyle,
    textFieldStyle: TextFieldStyle = JewelTheme.textFieldStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    searchMatchStyle: SearchMatchStyle = JewelTheme.searchMatchStyle,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable SpeedSearchScope.() -> Unit,
) {
    val intSource = interactionSource ?: remember { MutableInteractionSource() }
    val currentMatcherBuilder = rememberUpdatedState(matcherBuilder)

    val state = remember { SpeedSearchStateImpl(currentMatcherBuilder) }
    val isFocused by intSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) { if (!isFocused) state.hideSearch() }

    Box(modifier = modifier) {
        val scope =
            remember(this, searchMatchStyle, state, intSource) {
                SpeedSearchScopeImpl(this, searchMatchStyle, state, intSource)
            }

        scope.content()

        if (state.isVisible) {
            SpeedSearchInput(
                state = state.textFieldState,
                hasMatch = state.hasMatches,
                position = state.position,
                styling = styling,
                textStyle = textStyle,
                textFieldStyle = textFieldStyle,
            )
        }
    }
}

@ExperimentalJewelApi
@ApiStatus.Experimental
public interface SpeedSearchScope : BoxScope {
    public val searchMatchStyle: SearchMatchStyle
    public val speedSearchState: SpeedSearchState
    public val interactionSource: MutableInteractionSource

    public fun processKeyEvent(event: KeyEvent): Boolean
}

@ExperimentalJewelApi
@ApiStatus.Experimental
public interface SpeedSearchState {
    public var position: Alignment.Vertical
    public val searchText: String
    public val isVisible: Boolean
    public val hasMatches: Boolean
    public val matchingIndexes: List<Int>

    public fun matchResultForText(text: String?): SpeedSearchMatcher.MatchResult

    public fun clearSearch(): Boolean

    public fun hideSearch(): Boolean

    public suspend fun attach(
        entriesFlow: StateFlow<List<String?>>,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    )
}

@ExperimentalJewelApi
@ApiStatus.Experimental
public interface NodeSearchMatchState {
    public val matchResult: SpeedSearchMatcher.MatchResult
    public val style: SearchMatchStyle
}

@ExperimentalJewelApi
@ApiStatus.Experimental
public val LocalNodeSearchMatchState: ProvidableCompositionLocal<NodeSearchMatchState?> = compositionLocalOf { null }

@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun ProvideSearchMatchState(
    speedSearchState: SpeedSearchState,
    textContent: String?,
    style: SearchMatchStyle,
    content: @Composable () -> Unit,
) {
    val matchResult by rememberUpdatedState(speedSearchState.matchResultForText(textContent))

    val nodeState =
        remember(matchResult, style) {
            object : NodeSearchMatchState {
                override val matchResult
                    get() = matchResult

                override val style: SearchMatchStyle
                    get() = style
            }
        }

    CompositionLocalProvider(LocalNodeSearchMatchState provides nodeState, content = content)
}

@Composable
private fun SpeedSearchInput(
    state: TextFieldState,
    hasMatch: Boolean,
    position: Alignment.Vertical,
    styling: SpeedSearchStyle,
    textStyle: TextStyle,
    textFieldStyle: TextFieldStyle,
) {
    val foregroundColor = styling.getCurrentForegroundColor(hasMatch, textFieldStyle, textStyle)

    val (anchor, alignment) =
        remember(position) {
            when (position) {
                Alignment.Bottom -> Alignment.BottomStart to Alignment.BottomEnd
                else -> Alignment.TopStart to Alignment.TopEnd
            }
        }

    Popup(popupPositionProvider = rememberComponentRectPositionProvider(anchor, alignment)) {
        val focusRequester = remember { FocusRequester() }

        BasicTextField(
            state = state,
            cursorBrush = SolidColor(foregroundColor),
            textStyle = textStyle.merge(TextStyle(color = foregroundColor)),
            modifier =
                Modifier.testTag("SpeedSearchArea.Input").focusRequester(focusRequester).onFirstVisible {
                    focusRequester.requestFocus()
                },
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
    override val searchMatchStyle: SearchMatchStyle,
    override val speedSearchState: SpeedSearchStateImpl,
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
            event.key == Key.Spacebar && textFieldState.text.isBlank() -> false
            !event.isReallyTypedEvent() -> false // Only handle printable keys
            else -> textFieldState.handleValidKeyInput(event)
        }
    }

    private fun hideSpeedSearch(): Boolean = speedSearchState.hideSearch()

    private fun clearSearchInput(): Boolean = speedSearchState.clearSearch()

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
        val char = event.toChar()

        if (!char.isLetterOrDigit() && !PUNCTUATION_MARKS.contains(char)) {
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
private fun KeyEvent.isReallyTypedEvent(): Boolean {
    val keyChar = toChar()
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
            !isMetaPressed && !isCtrlPressed
        }
        else -> {
            !isAltPressed && !isCtrlPressed
        }
    }
}

@ExperimentalJewelApi
@ApiStatus.Experimental
internal class SpeedSearchStateImpl(private val matcherBuilderState: State<(String) -> SpeedSearchMatcher>) :
    SpeedSearchState {
    internal val textFieldState = TextFieldState()
    private var allMatches: Map<String?, SpeedSearchMatcher.MatchResult> by mutableStateOf(emptyMap())
    override val searchText: String by derivedStateOf { textFieldState.text.toString() }

    override var position: Alignment.Vertical by mutableStateOf(Alignment.Top)

    override var isVisible: Boolean by mutableStateOf(false)
        internal set

    override var hasMatches: Boolean by mutableStateOf(true)
        private set

    override var matchingIndexes: List<Int> by mutableStateOf(emptyList<Int>())
        private set

    override fun matchResultForText(text: String?): SpeedSearchMatcher.MatchResult =
        allMatches[text] ?: SpeedSearchMatcher.MatchResult.NoMatch

    override fun clearSearch(): Boolean {
        if (!isVisible) return false
        textFieldState.edit { delete(0, length) }
        return true
    }

    override fun hideSearch(): Boolean {
        if (!clearSearch()) return false
        isVisible = false
        return true
    }

    // Using only 'derivedStates' caused a lot of recompositions and caused a rendering lag.
    // To prevent this issue, I'm aggregating the states in this method and posting the values
    // to the relevant properties.
    override suspend fun attach(entriesFlow: StateFlow<List<String?>>, dispatcher: CoroutineDispatcher) {
        val searchTextFlow = snapshotFlow { searchText }
        val matcherBuilderFlow = snapshotFlow { matcherBuilderState.value }

        combine(searchTextFlow, matcherBuilderFlow) { text, buildMatcher ->
                val items = entriesFlow.value

                if (text.isBlank() || items.isEmpty()) {
                    allMatches = emptyMap()
                    matchingIndexes = emptyList()
                    hasMatches = true
                    return@combine
                }

                val matcher = buildMatcher(text)

                // Please note that use the default capacity can have a significant impact on performance for larger
                // data sets. After the first "round", we can start creating the array with an "educated guess" to
                // prevent tons of array copy in memory
                val newMatchingIndexes = ArrayList<Int>(matchingIndexes.size.takeIf { it > 0 } ?: 128)
                val newMatches = hashMapOf<String?, SpeedSearchMatcher.MatchResult>()
                var anyMatch = false

                for (index in items.indices) {
                    val item = items[index]
                    val matches = matcher.matches(item)

                    if (matches is SpeedSearchMatcher.MatchResult.Match) {
                        newMatchingIndexes.add(index)
                        newMatches[item] = matches
                        anyMatch = true
                    }
                }

                newMatchingIndexes.trimToSize()

                allMatches = newMatches
                matchingIndexes = newMatchingIndexes
                hasMatches = anyMatch
            }
            .flowOn(dispatcher)
            .collect()
    }
}

private fun KeyEvent.toChar(): Char =
    when (key) {
        Key.Spacebar -> ' '
        else -> utf16CodePoint.toChar()
    }

/**
 * **Swing Version:**
 * [SpeedSearch.PUNCTUATION_MARKS](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/speedSearch/SpeedSearch.java)
 */
private const val PUNCTUATION_MARKS = "*_-+\"'/.#$>: ,;?!@%^&"
