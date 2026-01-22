// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberComponentRectPositionProvider
import java.awt.event.KeyEvent as AWTKeyEvent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.search.EmptySpeedSearchMatcher
import org.jetbrains.jewel.foundation.search.SpeedSearchMatcher
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.SearchMatchStyle
import org.jetbrains.jewel.ui.component.styling.SpeedSearchStyle
import org.jetbrains.jewel.ui.component.styling.TextFieldStyle
import org.jetbrains.jewel.ui.theme.searchMatchStyle
import org.jetbrains.jewel.ui.theme.speedSearchStyle
import org.jetbrains.jewel.ui.theme.textFieldStyle
import org.jetbrains.jewel.ui.util.handleKeyEvent

@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
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
    SpeedSearchArea(
        modifier = modifier,
        styling = styling,
        textFieldStyle = textFieldStyle,
        textStyle = textStyle,
        searchMatchStyle = searchMatchStyle,
        interactionSource = interactionSource,
        state = rememberSpeedSearchState(matcherBuilder),
        content = content,
    )
}

/**
 * Creates a speed search area that provides keyboard-driven search functionality for its content.
 *
 * This composable enables users to quickly filter or highlight items by typing characters. The search input appears as
 * an overlay when the user starts typing, and matches are computed using the provided [matcherBuilder].
 *
 * @param modifier The modifier to be applied to the container.
 * @param matcherBuilder A function that creates a [SpeedSearchMatcher] from the search text. Defaults to
 *   [SpeedSearchMatcher.patternMatcher].
 * @param styling The visual styling for the speed search input overlay.
 * @param textFieldStyle The styling for the text field within the search overlay.
 * @param textStyle The text style for the search input text.
 * @param searchMatchStyle The styling for highlighting matched text in search results.
 * @param interactionSource The interaction source for tracking focus state. If null, a new one will be created.
 * @param dismissOnLoseFocus Whether to automatically hide the search input when it loses focus. Defaults to true.
 * @param content The content to be displayed within the speed search area. Use [SpeedSearchScope] to access search
 *   state and process key events.
 */
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
    dismissOnLoseFocus: Boolean = true,
    content: @Composable SpeedSearchScope.() -> Unit,
) {
    SpeedSearchArea(
        modifier = modifier,
        styling = styling,
        textFieldStyle = textFieldStyle,
        textStyle = textStyle,
        searchMatchStyle = searchMatchStyle,
        interactionSource = interactionSource,
        state = rememberSpeedSearchState(matcherBuilder),
        dismissOnLoseFocus = dismissOnLoseFocus,
        content = content,
    )
}

/**
 * Creates a speed search area with an externally managed [SpeedSearchState].
 *
 * This overload allows for more control over the speed search state, enabling features like sharing state between
 * components or programmatically controlling the search.
 *
 * @param state The [SpeedSearchState] that manages the search functionality and provides access to search results and
 *   matching logic.
 * @param modifier The modifier to be applied to the container.
 * @param styling The visual styling for the speed search input overlay.
 * @param textFieldStyle The styling for the text field within the search overlay.
 * @param textStyle The text style for the search input text.
 * @param searchMatchStyle The styling for highlighting matched text in search results.
 * @param interactionSource The interaction source for tracking focus state. If null, a new one will be created.
 * @param dismissOnLoseFocus Whether to automatically hide the search input when it loses focus. Defaults to true.
 * @param content The content to be displayed within the speed search area. Use [SpeedSearchScope] to access search
 *   state and process key events.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun SpeedSearchArea(
    state: SpeedSearchState,
    modifier: Modifier = Modifier,
    styling: SpeedSearchStyle = JewelTheme.speedSearchStyle,
    textFieldStyle: TextFieldStyle = JewelTheme.textFieldStyle,
    textStyle: TextStyle = JewelTheme.defaultTextStyle,
    searchMatchStyle: SearchMatchStyle = JewelTheme.searchMatchStyle,
    interactionSource: MutableInteractionSource? = null,
    dismissOnLoseFocus: Boolean = true,
    content: @Composable SpeedSearchScope.() -> Unit,
) {
    val intSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by intSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) { if (!isFocused && dismissOnLoseFocus) state.hideSearch() }

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

/**
 * Scope for the content of a [SpeedSearchArea], providing access to search state and styling.
 *
 * This scope extends [BoxScope] and provides additional properties and functions specific to speed search
 * functionality.
 */
@ExperimentalJewelApi
@ApiStatus.Experimental
public interface SpeedSearchScope : BoxScope {
    /** The style to use for highlighting search matches in the content. */
    public val searchMatchStyle: SearchMatchStyle

    /** The current state of the speed search, including search text, visibility, and matching results. */
    public val speedSearchState: SpeedSearchState

    /** The interaction source for tracking user interactions with the search input. */
    public val interactionSource: MutableInteractionSource

    /**
     * Processes a keyboard event, potentially updating the search state or input.
     *
     * @param event The keyboard event to process.
     * @return `true` if the event was handled and should not be propagated further, `false` otherwise.
     */
    public fun processKeyEvent(event: KeyEvent): Boolean
}

/**
 * State holder for speed search functionality, managing search text, visibility, and match results.
 *
 * This interface provides access to the current search state and methods for controlling the search behavior. Use
 * [rememberSpeedSearchState] to create an instance of this state.
 */
@ExperimentalJewelApi
@ApiStatus.Experimental
public interface SpeedSearchState {
    /** The vertical position of the search input overlay (Top or Bottom). */
    public var position: Alignment.Vertical

    /** The current search text entered by the user. */
    public val searchText: String

    /** The current [SpeedSearchMatcher] used to match items against the search text. */
    public val currentMatcher: SpeedSearchMatcher

    /** Whether the search input overlay is currently visible. */
    public var isVisible: Boolean

    /** Whether there are any matches for the current search text. */
    public val hasMatches: Boolean

    /** List of indices of items that match the current search text. */
    public val matchingIndexes: List<Int>

    /**
     * Internal text field state. Should not be accessed directly by public API consumers.
     *
     * This property is exposed with [InternalJewelApi] to allow internal Jewel components (such as [SpeedSearchArea]
     * and [SpeedSearchScope]) to access the underlying text field for keyboard event handling, cursor management, and
     * text manipulation. Public API consumers should use the [searchText] property to read the current search query.
     */
    @InternalJewelApi @get:ApiStatus.Internal public val textFieldState: TextFieldState

    /**
     * Retrieves the match result for a specific text value.
     *
     * @param text The text to check for matches against the current search query.
     * @return The match result indicating whether and where the text matches.
     */
    public fun matchResultForText(text: String?): SpeedSearchMatcher.MatchResult

    /**
     * Clears the search text without hiding the search input.
     *
     * @return `true` if the search was cleared, `false` if it was already empty or not visible.
     */
    public fun clearSearch(): Boolean

    /**
     * Hides the search input and clears the search text.
     *
     * @return `true` if the search was hidden, `false` if it was already hidden.
     */
    public fun hideSearch(): Boolean

    /**
     * Attaches the speed search state to a flow of searchable entries.
     *
     * This suspending function continuously listens to changes in the entries and updates the matching results
     * accordingly. It should be launched in a coroutine scope that matches the lifetime of the component using the
     * speed search.
     *
     * @param entriesFlow A [StateFlow] containing the list of searchable items.
     * @param dispatcher The coroutine dispatcher to use for search matching operations. Defaults to
     *   [Dispatchers.Default].
     */
    public suspend fun attach(
        entriesFlow: StateFlow<List<String?>>,
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    )
}

/**
 * Creates and remembers a [SpeedSearchState] instance.
 *
 * @param matcherBuilder A function that creates a [SpeedSearchMatcher] from the search text. Defaults to
 *   [SpeedSearchMatcher.patternMatcher].
 * @return A remembered [SpeedSearchState] instance that will be preserved across recompositions.
 */
@Composable
@ExperimentalJewelApi
@ApiStatus.Experimental
public fun rememberSpeedSearchState(
    matcherBuilder: (String) -> SpeedSearchMatcher = SpeedSearchMatcher::patternMatcher
): SpeedSearchState {
    val currentMatcherBuilder = rememberUpdatedState(matcherBuilder)
    val searchState = rememberSearchTextFieldState()
    return remember { SpeedSearchStateImpl(searchState, currentMatcherBuilder) }
}

/**
 * State that provides match result and styling information for an individual node in the search results.
 *
 * This interface is typically provided through [LocalNodeSearchMatchState] composition local and accessed by child
 * composables that need to render search match highlights.
 */
@ExperimentalJewelApi
@ApiStatus.Experimental
public interface NodeSearchMatchState {
    /** The match result for this specific node, indicating whether and how it matches the search query. */
    public val matchResult: SpeedSearchMatcher.MatchResult

    /** The styling to apply when highlighting search matches in this node. */
    public val style: SearchMatchStyle
}

/**
 * Composition local that provides [NodeSearchMatchState] to child composables.
 *
 * Use [ProvideSearchMatchState] to set this value for a specific composable tree.
 */
@ExperimentalJewelApi
@ApiStatus.Experimental
public val LocalNodeSearchMatchState: ProvidableCompositionLocal<NodeSearchMatchState?> = compositionLocalOf { null }

/**
 * Provides search match state to child composables through [LocalNodeSearchMatchState].
 *
 * This composable computes the match result for the given [textContent] using the current speed search state and makes
 * it available to descendants via composition local.
 *
 * @param speedSearchState The current speed search state containing the search query.
 * @param textContent The text content to check for matches. If null, no match will be found.
 * @param style The styling to use when highlighting matches.
 * @param content The child composables that can access the match state through [LocalNodeSearchMatchState].
 */
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
    val (anchor, alignment) =
        remember(position) {
            when (position) {
                Alignment.Bottom -> Alignment.BottomStart to Alignment.BottomEnd
                else -> Alignment.TopStart to Alignment.TopEnd
            }
        }

    Popup(popupPositionProvider = rememberComponentRectPositionProvider(anchor, alignment)) {
        val focusRequester = remember { FocusRequester() }

        SearchTextField(
            state = rememberSearchTextFieldState(state),
            textStyle = textStyle,
            allowClear = false,
            error = !hasMatch,
            textFieldStyle = textFieldStyle,
            modifier =
                Modifier.testTag("SpeedSearchArea.Input")
                    .background(styling.colors.background)
                    .border(1.dp, styling.colors.border)
                    .focusRequester(focusRequester)
                    .onFirstVisible { focusRequester.requestFocus() },
        )
    }
}

private class SpeedSearchScopeImpl(
    val delegate: BoxScope,
    override val searchMatchStyle: SearchMatchStyle,
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
            else ->
                speedSearchState.textFieldState.handleKeyEvent(
                    event = event,
                    allowNavigationWithArrowKeys = false,
                    allowedSymbols = PUNCTUATION_MARKS,
                    onTextChange = { text ->
                        if (!speedSearchState.isVisible && text.isNotEmpty()) {
                            speedSearchState.isVisible = true
                        }
                    },
                )
        }
    }

    private fun hideSpeedSearch(): Boolean = speedSearchState.hideSearch()

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
}

@ExperimentalJewelApi
@ApiStatus.Experimental
internal class SpeedSearchStateImpl(
    private val searchState: SearchTextFieldState,
    private val matcherBuilderState: State<(String) -> SpeedSearchMatcher>,
) : SpeedSearchState {
    override val textFieldState
        get() = searchState.textFieldState

    private var allMatches: Map<String?, SpeedSearchMatcher.MatchResult> by mutableStateOf(emptyMap())
    override var searchText: String by mutableStateOf("")

    override var position: Alignment.Vertical by mutableStateOf(Alignment.Top)
    override var isVisible: Boolean by mutableStateOf(false)

    override var hasMatches: Boolean by mutableStateOf(true)
        private set

    override var matchingIndexes: List<Int> by mutableStateOf(emptyList<Int>())
        private set

    override var currentMatcher by mutableStateOf<SpeedSearchMatcher>(EmptySpeedSearchMatcher)
        private set

    override fun matchResultForText(text: String?): SpeedSearchMatcher.MatchResult =
        allMatches[text] ?: SpeedSearchMatcher.MatchResult.NoMatch

    override fun clearSearch(): Boolean {
        if (!isVisible) return false
        Snapshot.withMutableSnapshot {
            textFieldState.edit { delete(0, length) }
            searchText = ""
        }
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
        val searchTextFlow = snapshotFlow { textFieldState.text.toString() }
        val matcherBuilderFlow = snapshotFlow { matcherBuilderState.value }

        createMatcherFlow(searchTextFlow, matcherBuilderFlow)
            .combineWithEntries(entriesFlow)
            .flowOn(dispatcher)
            .collect()
    }

    private fun createMatcherFlow(
        searchTextFlow: Flow<String>,
        matcherBuilderFlow: Flow<(String) -> SpeedSearchMatcher>,
    ) =
        combine(searchTextFlow, matcherBuilderFlow) { text, buildMatcher ->
            val matcher =
                if (text.isBlank()) {
                    EmptySpeedSearchMatcher
                } else {
                    buildMatcher(text).cached()
                }

            text to matcher
        }

    private fun Flow<Pair<String, SpeedSearchMatcher>>.combineWithEntries(entriesFlow: StateFlow<List<String?>>) =
        combine(entriesFlow) { (text, matcher), items ->
            if (text.isBlank()) {
                // Batch all state updates in a single snapshot to prevent intermediate recompositions.
                // This ensures atomicity when resetting the search state and improves performance by
                // triggering only one recomposition instead of four separate ones.
                Snapshot.withMutableSnapshot {
                    allMatches = emptyMap()
                    matchingIndexes = emptyList()
                    searchText = ""
                    hasMatches = true
                    currentMatcher = matcher
                }
                return@combine
            }

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

            // Batch all state updates in a single snapshot to prevent intermediate recompositions.
            // This ensures atomicity when updating search results and improves performance by
            // triggering only one recomposition instead of four separate ones.
            Snapshot.withMutableSnapshot {
                allMatches = newMatches
                matchingIndexes = newMatchingIndexes
                hasMatches = anyMatch
                searchText = text
                currentMatcher = matcher
            }
        }
}

/**
 * **Swing Version:**
 * [SpeedSearch.PUNCTUATION_MARKS](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-api/src/com/intellij/ui/speedSearch/SpeedSearch.java)
 */
private const val PUNCTUATION_MARKS = "*_-+\"'/.#$>: ,;?!@%^&"

private fun SpeedSearchMatcher.cached() =
    object : SpeedSearchMatcher {
        private val cache = LRUCache<CharSequence, SpeedSearchMatcher.MatchResult>(100)

        override fun matches(text: String?): SpeedSearchMatcher.MatchResult = matches(text as? CharSequence)

        override fun matches(text: CharSequence?): SpeedSearchMatcher.MatchResult =
            if (text.isNullOrBlank()) {
                this@cached.matches(text)
            } else {
                cache.getOrPut(text) { this@cached.matches(text) }
            }
    }

private class LRUCache<K : Any, V : Any>(private val capacity: Int) : LinkedHashMap<K, V>(capacity, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > capacity
}
