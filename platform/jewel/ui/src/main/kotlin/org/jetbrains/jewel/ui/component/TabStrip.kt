package org.jetbrains.jewel.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.util.fastRoundToInt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.state.CommonStateBitMask
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.ui.component.styling.TabStyle

/**
 * A horizontal strip of tabs that follows the standard visual styling with customizable appearance.
 *
 * Provides a scrollable container for displaying a row of tabs, with support for selection, hover effects, and
 * automatic scrollbar visibility. The tabs can be closable or fixed, and support custom content layouts.
 *
 * **Guidelines:** [on IJP SDK webhelp](https://plugins.jetbrains.com/docs/intellij/tabs.html)
 *
 * **Usage example:**
 * [`Tabs.kt`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Tabs.kt)
 *
 * **Swing equivalent:** [`JTabbedPane`](https://docs.oracle.com/javase/tutorial/uiswing/components/tabbedpane.html)
 *
 * @param tabs The list of tab data objects defining the content and behavior of each tab
 * @param style The visual styling configuration for the tab strip and its tabs
 * @param modifier Modifier to be applied to the tab strip container
 * @param enabled Controls the enabled state of the tab strip. When false, no tabs can be interacted with
 * @param interactionSource The [MutableInteractionSource] that will be used to handle interactions with the tab strip
 * @see javax.swing.JTabbedPane
 * @see com.intellij.ui.components.JBTabbedPane
 */
@Composable
public fun TabStrip(
    tabs: List<TabData>,
    style: TabStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var tabStripState: TabStripState by remember { mutableStateOf(TabStripState.of(enabled = true)) }
    val selectedIndex = remember(tabs) { tabs.indexOfFirst { it.selected } }
    val tabCount = tabs.size

    remember(enabled) { tabStripState = tabStripState.copy(enabled) }

    val scrollState = rememberScrollState()

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is FocusInteraction ->
                    tabStripState = tabStripState.copy(focused = interaction is FocusInteraction.Focus)

                is HoverInteraction ->
                    tabStripState = tabStripState.copy(hovered = interaction is HoverInteraction.Enter)
            }
        }
    }

    Box(
        modifier
            .onPreviewKeyEvent { event ->
                if (!enabled || tabs.isEmpty()) return@onPreviewKeyEvent false

                when (event.type) {
                    KeyEventType.KeyDown ->
                        when (event.key) {
                            Key.DirectionLeft -> {
                                tabs[if (selectedIndex > 0) (selectedIndex - 1) else tabs.lastIndex].onClick()
                                true
                            }

                            Key.DirectionRight -> {
                                tabs[(selectedIndex + 1) % tabCount].onClick()
                                true
                            }

                            else -> false
                        }

                    KeyEventType.KeyUp ->
                        when (event.key) {
                            Key.MoveHome -> {
                                tabs.first().onClick()
                                true
                            }

                            Key.MoveEnd -> {
                                tabs.last().onClick()
                                true
                            }

                            else -> false
                        }

                    else -> false
                }
            }
            .focusable(enabled, interactionSource)
            .hoverable(interactionSource, enabled)
    ) {
        Row(
            modifier =
                Modifier.horizontalScroll(scrollState).selectableGroup().semantics {
                    collectionInfo = CollectionInfo(rowCount = 1, columnCount = tabCount)
                }
        ) {
            var selectedCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

            tabs.forEachIndexed { index, tab ->
                TabImpl(
                    isActive = tabStripState.isActive,
                    tabData = tab,
                    tabStyle = style,
                    tabIndex = index,
                    tabCount = tabCount,
                    modifier =
                        Modifier.onPlaced {
                            if (tab.selected) {
                                selectedCoordinates = it
                            }
                        },
                )
            }

            LaunchedEffect(selectedCoordinates) {
                val coordinates = selectedCoordinates ?: return@LaunchedEffect

                val initialBound = scrollState.value
                val tabStartPosition = coordinates.positionInParent().x.fastRoundToInt()

                if (tabStartPosition < initialBound) {
                    // If navigating back, scroll to the start of the tab
                    scrollState.animateScrollTo(tabStartPosition)
                    return@LaunchedEffect
                }

                val finalBound = initialBound + scrollState.viewportSize
                val tabEndPosition = tabStartPosition + coordinates.size.width

                if (tabEndPosition > finalBound) {
                    // If navigating forward, move the scroll just enough to show the tab
                    scrollState.animateScrollBy((tabEndPosition - finalBound).toFloat())
                    return@LaunchedEffect
                }
            }
        }

        AnimatedVisibility(
            visible = tabStripState.isHovered,
            enter = fadeIn(tween(durationMillis = 125, delayMillis = 0, easing = LinearEasing)),
            exit = fadeOut(tween(durationMillis = 125, delayMillis = 700, easing = LinearEasing)),
        ) {
            HorizontalScrollbar(scrollState, style = style.scrollbarStyle, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Deprecated("Use the TabStrip function with a interactionSource parameter")
@ApiStatus.Experimental
@ExperimentalJewelApi
@Composable
public fun TabStrip(tabs: List<TabData>, style: TabStyle, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TabStrip(tabs, style, modifier, enabled, remember { MutableInteractionSource() })
}

@Immutable
public sealed class TabData {
    public abstract val selected: Boolean
    public abstract val content: @Composable TabContentScope.(tabState: TabState) -> Unit
    public abstract val closable: Boolean
    public abstract val onClose: () -> Unit
    public abstract val onClick: () -> Unit

    /**
     * Standard tab implementation suitable for most use cases.
     *
     * This implementation provides the standard tab behavior and appearance, making it suitable for general-purpose
     * tabs in dialogs, tool windows, and other UI components.
     *
     * @property selected Whether this tab is currently selected
     * @property content The composable content to be displayed within the tab
     * @property closable Whether this tab can be closed by the user (defaults to true)
     * @property onClose Called when the user attempts to close the tab
     * @property onClick Called when the user clicks the tab
     */
    @Immutable
    @GenerateDataFunctions
    public class Default(
        override val selected: Boolean,
        override val content: @Composable TabContentScope.(tabState: TabState) -> Unit,
        override val closable: Boolean = true,
        override val onClose: () -> Unit = {},
        override val onClick: () -> Unit = {},
    ) : TabData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Default

            if (selected != other.selected) return false
            if (closable != other.closable) return false
            if (content != other.content) return false
            if (onClose != other.onClose) return false
            if (onClick != other.onClick) return false

            return true
        }

        override fun hashCode(): Int {
            var result = selected.hashCode()
            result = 31 * result + closable.hashCode()
            result = 31 * result + content.hashCode()
            result = 31 * result + onClose.hashCode()
            result = 31 * result + onClick.hashCode()
            return result
        }

        override fun toString(): String {
            return "Default(" +
                "selected=$selected, " +
                "content=$content, " +
                "closable=$closable, " +
                "onClose=$onClose, " +
                "onClick=$onClick" +
                ")"
        }
    }

    /**
     * Editor-specific tab implementation with specialized styling and behavior.
     *
     * This implementation is specifically designed for editor tabs, providing the appropriate styling and behavior for
     * displaying file editors. It matches the appearance of IDE editor tabs.
     *
     * @property selected Whether this tab is currently selected
     * @property content The composable content to be displayed within the tab
     * @property closable Whether this tab can be closed by the user (defaults to true)
     * @property onClose Called when the user attempts to close the tab
     * @property onClick Called when the user clicks the tab
     */
    @Immutable
    @GenerateDataFunctions
    public class Editor(
        override val selected: Boolean,
        override val content: @Composable TabContentScope.(tabState: TabState) -> Unit,
        override val closable: Boolean = true,
        override val onClose: () -> Unit = {},
        override val onClick: () -> Unit = {},
    ) : TabData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Editor

            if (selected != other.selected) return false
            if (closable != other.closable) return false
            if (content != other.content) return false
            if (onClose != other.onClose) return false
            if (onClick != other.onClick) return false

            return true
        }

        override fun hashCode(): Int {
            var result = selected.hashCode()
            result = 31 * result + closable.hashCode()
            result = 31 * result + content.hashCode()
            result = 31 * result + onClose.hashCode()
            result = 31 * result + onClick.hashCode()
            return result
        }

        override fun toString(): String {
            return "Editor(" +
                "selected=$selected, " +
                "content=$content, " +
                "closable=$closable, " +
                "onClose=$onClose, " +
                "onClick=$onClick" +
                ")"
        }
    }
}

/**
 * State holder for the tab strip component that tracks various interaction states.
 *
 * This class maintains the state of a tab strip, including enabled/disabled state, focus, hover, and press states. It
 * uses a bit-masked value for efficient state storage and implements [FocusableComponentState] for consistent behavior
 * with other focusable components.
 *
 * @property state The raw bit-masked state value
 * @see FocusableComponentState
 */
@Immutable
@JvmInline
public value class TabStripState(public val state: ULong) : FocusableComponentState {
    override val isActive: Boolean
        get() = state and CommonStateBitMask.Active != 0UL

    override val isEnabled: Boolean
        get() = state and CommonStateBitMask.Enabled != 0UL

    override val isFocused: Boolean
        get() = state and CommonStateBitMask.Focused != 0UL

    override val isHovered: Boolean
        get() = state and CommonStateBitMask.Hovered != 0UL

    override val isPressed: Boolean
        get() = state and CommonStateBitMask.Pressed != 0UL

    public fun copy(
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): TabStripState = of(enabled = enabled, focused = focused, pressed = pressed, hovered = hovered, active = active)

    override fun toString(): String =
        "${javaClass.simpleName}(isEnabled=$isEnabled, isFocused=$isFocused, isHovered=$isHovered, " +
            "isPressed=$isPressed, isActive=$isActive)"

    public companion object {
        public fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): TabStripState =
            TabStripState(
                (if (enabled) CommonStateBitMask.Enabled else 0UL) or
                    (if (focused) CommonStateBitMask.Focused else 0UL) or
                    (if (hovered) CommonStateBitMask.Hovered else 0UL) or
                    (if (pressed) CommonStateBitMask.Pressed else 0UL) or
                    (if (active) CommonStateBitMask.Active else 0UL)
            )
    }
}
