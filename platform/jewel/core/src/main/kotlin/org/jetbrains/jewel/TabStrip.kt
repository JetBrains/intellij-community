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
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.jewel.foundation.onHover

@Composable
fun TabStrip(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: TabStripScope.() -> Unit,
) {
    val tabsData = remember { content.asList() }

    var tabStripState: TabStripState by remember { mutableStateOf(TabStripState.of(enabled = true)) }

    remember(enabled) { tabStripState = tabStripState.copy(enabled) }

    val scrollState = rememberScrollState()
    Box(
        modifier
            .focusable(true, remember { MutableInteractionSource() })
            .onHover { tabStripState = tabStripState.copy(hovered = it) }
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .scrollable(
                    orientation = Orientation.Vertical,
                    reverseDirection = ScrollableDefaults.reverseDirection(
                        LocalLayoutDirection.current,
                        Orientation.Vertical,
                        false
                    ),
                    state = scrollState,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .selectableGroup()
        ) {
            tabsData.forEach {
                TabImpl(isActive = tabStripState.isActive, tabData = it)
            }
        }
        AnimatedVisibility(
            visible = tabStripState.isHovered,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = 125,
                    delayMillis = 0,
                    easing = LinearEasing
                )
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = 125,
                    delayMillis = 700,
                    easing = LinearEasing
                )
            )
        ) {
            TabStripHorizontalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

interface TabStripScope {

    fun tab(
        selected: Boolean,
        label: String,
        tabIconResource: String? = null,
        closable: Boolean = true,
        onClose: () -> Unit = {},
        onClick: () -> Unit = {},
    )

    fun tabs(
        tabsCount: Int,
        selected: (Int) -> Boolean,
        label: (Int) -> String,
        tabIconResource: (Int) -> String?,
        closable: (Int) -> Boolean,
        onClose: (Int) -> Unit,
        onClick: (Int) -> Unit,
    )

    fun editorTab(
        selected: Boolean,
        label: String,
        tabIconResource: String? = null,
        closable: Boolean = true,
        onClose: () -> Unit = {},
        onClick: () -> Unit = {},
    )

    fun editorTabs(
        tabsCount: Int,
        selected: (Int) -> Boolean,
        label: (Int) -> String,
        tabIconResource: (Int) -> String?,
        closable: (Int) -> Boolean,
        onClose: (Int) -> Unit,
        onClick: (Int) -> Unit,
    )
}

sealed class TabData(
    val selected: Boolean,
    val label: String,
    val tabIconResource: String? = null,
    val closable: Boolean = true,
    val onClose: () -> Unit = {},
    val onClick: () -> Unit = {},
) {

    class Default(
        selected: Boolean,
        label: String,
        tabIconResource: String? = null,
        closable: Boolean = true,
        onClose: () -> Unit = {},
        onClick: () -> Unit = {},
    ) : TabData(
        selected,
        label,
        tabIconResource,
        closable,
        onClose,
        onClick
    )

    class Editor(
        selected: Boolean,
        label: String,
        tabIconResource: String? = null,
        closable: Boolean = true,
        onClose: () -> Unit = {},
        onClick: () -> Unit = {},
    ) : TabData(
        selected,
        label,
        tabIconResource,
        closable,
        onClose,
        onClick
    )
}

private fun (TabStripScope.() -> Unit).asList() = buildList {
    this@asList(
        object : TabStripScope {
            override fun tab(
                selected: Boolean,
                label: String,
                tabIconResource: String?,
                closable: Boolean,
                onClose: () -> Unit,
                onClick: () -> Unit,
            ) {
                add(
                    TabData.Default(
                        selected = selected,
                        label = label,
                        tabIconResource = tabIconResource,
                        closable = closable,
                        onClose = onClose,
                        onClick = onClick
                    )
                )
            }

            override fun tabs(
                tabsCount: Int,
                selected: (Int) -> Boolean,
                label: (Int) -> String,
                tabIconResource: (Int) -> String?,
                closable: (Int) -> Boolean,
                onClose: (Int) -> Unit,
                onClick: (Int) -> Unit,
            ) {
                repeat(tabsCount) {
                    tab(
                        selected(it),
                        label(it),
                        tabIconResource(it),
                        closable(it),
                        { onClose(it) },
                        { onClick(it) }
                    )
                }
            }

            override fun editorTab(
                selected: Boolean,
                label: String,
                tabIconResource: String?,
                closable: Boolean,
                onClose: () -> Unit,
                onClick: () -> Unit,
            ) {
                add(
                    TabData.Editor(
                        selected = selected,
                        label = label,
                        tabIconResource = tabIconResource,
                        closable = closable,
                        onClose = onClose,
                        onClick = onClick
                    )
                )
            }

            override fun editorTabs(
                tabsCount: Int,
                selected: (Int) -> Boolean,
                label: (Int) -> String,
                tabIconResource: (Int) -> String?,
                closable: (Int) -> Boolean,
                onClose: (Int) -> Unit,
                onClick: (Int) -> Unit,
            ) {
                repeat(tabsCount) {
                    editorTab(
                        selected(it),
                        label(it),
                        tabIconResource(it),
                        closable(it),
                        { onClose(it) },
                        { onClick(it) }
                    )
                }
            }
        }
    )
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
        active = active
    )

    override fun toString() =
        "${javaClass.simpleName}(isEnabled=$isEnabled, isFocused=$isFocused, isHovered=$isHovered, " +
            "isPressed=$isPressed, isActive=$isActive)"

    companion object {

        fun of(
            enabled: Boolean = true,
            focused: Boolean = false,
            error: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            warning: Boolean = false,
            active: Boolean = false,
        ) = TabStripState(
            state = (if (enabled) CommonStateBitMask.Enabled else 0UL) or
                (if (focused) CommonStateBitMask.Focused else 0UL) or
                (if (hovered) CommonStateBitMask.Hovered else 0UL) or
                (if (pressed) CommonStateBitMask.Pressed else 0UL) or
                (if (warning) CommonStateBitMask.Warning else 0UL) or
                (if (error) CommonStateBitMask.Error else 0UL) or
                (if (active) CommonStateBitMask.Active else 0UL)
        )
    }
}
