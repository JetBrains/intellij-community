package org.jetbrains.jewel.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.modifier.onHover
import org.jetbrains.jewel.foundation.state.CommonStateBitMask
import org.jetbrains.jewel.foundation.state.FocusableComponentState
import org.jetbrains.jewel.ui.component.styling.TabStyle

@Composable
public fun TabStrip(tabs: List<TabData>, style: TabStyle, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var tabStripState: TabStripState by remember { mutableStateOf(TabStripState.of(enabled = true)) }

    remember(enabled) { tabStripState = tabStripState.copy(enabled) }

    val scrollState = rememberScrollState()
    Box(
        modifier.focusable(true, remember { MutableInteractionSource() }).onHover {
            tabStripState = tabStripState.copy(hovered = it)
        }
    ) {
        Row(
            modifier =
                Modifier.horizontalScroll(scrollState)
                    .scrollable(
                        orientation = Orientation.Vertical,
                        reverseDirection =
                            ScrollableDefaults.reverseDirection(
                                LocalLayoutDirection.current,
                                Orientation.Vertical,
                                false,
                            ),
                        state = scrollState,
                        interactionSource = remember { MutableInteractionSource() },
                    )
                    .selectableGroup()
        ) {
            tabs.forEach { TabImpl(isActive = tabStripState.isActive, tabData = it) }
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

@Immutable
public sealed class TabData {
    public abstract val selected: Boolean
    public abstract val content: @Composable TabContentScope.(tabState: TabState) -> Unit
    public abstract val closable: Boolean
    public abstract val onClose: () -> Unit
    public abstract val onClick: () -> Unit

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
