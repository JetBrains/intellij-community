package org.jetbrains.jewel

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
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.jewel.foundation.onHover

@Composable
fun TabStrip(
    tabs: List<TabData>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var tabStripState: TabStripState by remember { mutableStateOf(TabStripState.of(enabled = true)) }

    remember(enabled) { tabStripState = tabStripState.copy(enabled) }

    val scrollState = rememberScrollState()
    Box(
        modifier
            .focusable(true, remember { MutableInteractionSource() })
            .onHover { tabStripState = tabStripState.copy(hovered = it) },
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .scrollable(
                    orientation = Orientation.Vertical,
                    reverseDirection = ScrollableDefaults.reverseDirection(
                        LocalLayoutDirection.current,
                        Orientation.Vertical,
                        false,
                    ),
                    state = scrollState,
                    interactionSource = remember { MutableInteractionSource() },
                )
                .selectableGroup(),
        ) {
            tabs.forEach {
                TabImpl(isActive = tabStripState.isActive, tabData = it)
            }
        }
        AnimatedVisibility(
            visible = tabStripState.isHovered,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 125,
                    delayMillis = 0,
                    easing = LinearEasing,
                ),
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 125,
                    delayMillis = 700,
                    easing = LinearEasing,
                ),
            ),
        ) {
            TabStripHorizontalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Immutable
sealed class TabData {

    abstract val selected: Boolean
    abstract val label: String
    abstract val icon: Painter?
    abstract val closable: Boolean
    abstract val onClose: () -> Unit
    abstract val onClick: () -> Unit

    @Immutable
    class Default(
        override val selected: Boolean,
        override val label: String,
        override val icon: Painter? = null,
        override val closable: Boolean = true,
        override val onClose: () -> Unit = {},
        override val onClick: () -> Unit = {},
    ) : TabData() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Default

            if (selected != other.selected) return false
            if (label != other.label) return false
            if (icon != other.icon) return false
            if (closable != other.closable) return false
            if (onClose != other.onClose) return false
            if (onClick != other.onClick) return false

            return true
        }

        override fun hashCode(): Int {
            var result = selected.hashCode()
            result = 31 * result + label.hashCode()
            result = 31 * result + (icon?.hashCode() ?: 0)
            result = 31 * result + closable.hashCode()
            result = 31 * result + onClose.hashCode()
            result = 31 * result + onClick.hashCode()
            return result
        }
    }

    @Immutable
    class Editor(
        override val selected: Boolean,
        override val label: String,
        override val icon: Painter? = null,
        override val closable: Boolean = true,
        override val onClose: () -> Unit = {},
        override val onClick: () -> Unit = {},
    ) : TabData() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Editor

            if (selected != other.selected) return false
            if (label != other.label) return false
            if (icon != other.icon) return false
            if (closable != other.closable) return false
            if (onClose != other.onClose) return false
            if (onClick != other.onClick) return false

            return true
        }

        override fun hashCode(): Int {
            var result = selected.hashCode()
            result = 31 * result + label.hashCode()
            result = 31 * result + (icon?.hashCode() ?: 0)
            result = 31 * result + closable.hashCode()
            result = 31 * result + onClose.hashCode()
            result = 31 * result + onClick.hashCode()
            return result
        }
    }
}

@Immutable
@JvmInline
value class TabStripState(val state: ULong) : FocusableComponentState {

    @Stable
    override val isActive: Boolean
        get() = state and CommonStateBitMask.Active != 0UL

    @Stable
    override val isEnabled: Boolean
        get() = state and CommonStateBitMask.Enabled != 0UL

    @Stable
    override val isFocused: Boolean
        get() = state and CommonStateBitMask.Focused != 0UL

    @Stable
    override val isHovered: Boolean
        get() = state and CommonStateBitMask.Hovered != 0UL

    @Stable
    override val isPressed: Boolean
        get() = state and CommonStateBitMask.Pressed != 0UL

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
        "${javaClass.simpleName}(isEnabled=$isEnabled, isFocused=$isFocused, isHovered=$isHovered, " +
            "isPressed=$isPressed, isActive=$isActive)"

    companion object {

        fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ) = TabStripState(
            state = (if (enabled) CommonStateBitMask.Enabled else 0UL) or
                (if (focused) CommonStateBitMask.Focused else 0UL) or
                (if (hovered) CommonStateBitMask.Hovered else 0UL) or
                (if (pressed) CommonStateBitMask.Pressed else 0UL) or
                (if (active) CommonStateBitMask.Active else 0UL),
        )
    }
}
