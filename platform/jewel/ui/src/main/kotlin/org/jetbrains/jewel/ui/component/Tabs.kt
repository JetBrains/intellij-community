package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiary
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Active
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Enabled
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Hovered
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Pressed
import org.jetbrains.jewel.foundation.state.CommonStateBitMask.Selected
import org.jetbrains.jewel.foundation.state.SelectableComponentState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.LocalContentColor
import org.jetbrains.jewel.ui.component.styling.TabStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.jetbrains.jewel.ui.theme.editorTabStyle

public interface TabContentScope {
    @Composable
    public fun Modifier.tabContentAlpha(state: TabState): Modifier =
        alpha(JewelTheme.editorTabStyle.contentAlpha.contentFor(state).value)
}

internal class TabContentScopeContainer : TabContentScope

@Composable
public fun TabContentScope.SimpleTabContent(
    label: String,
    state: TabState,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
) {
    SimpleTabContent(
        state = state,
        modifier = modifier,
        icon = icon?.let { { Icon(painter = icon, contentDescription = null) } },
        label = { Text(label) },
    )
}

@Composable
public fun TabContentScope.SimpleTabContent(
    label: String,
    state: TabState,
    modifier: Modifier = Modifier,
    iconKey: IconKey? = null,
    vararg painterHints: PainterHint,
) {
    SimpleTabContent(
        state = state,
        modifier = modifier,
        icon = iconKey?.let { { Icon(key = iconKey, contentDescription = null, hints = painterHints) } },
        label = { Text(label) },
    )
}

@Composable
public fun TabContentScope.SimpleTabContent(
    state: TabState,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    label: @Composable () -> Unit,
) {
    Row(
        modifier.tabContentAlpha(state),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space = JewelTheme.defaultTabStyle.metrics.tabContentSpacing),
    ) {
        if (icon != null) {
            icon()
        }
        label()
    }
}

@Composable
internal fun TabImpl(
    isActive: Boolean,
    tabData: TabData,
    tabStyle: TabStyle,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var tabState by remember { mutableStateOf(TabState.of(selected = tabData.selected, active = isActive)) }
    remember(tabData.selected, isActive) { tabState = tabState.copy(selected = tabData.selected, active = isActive) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> tabState = tabState.copy(pressed = true)
                is PressInteraction.Cancel,
                is PressInteraction.Release -> tabState = tabState.copy(pressed = false)
                is HoverInteraction.Enter -> tabState = tabState.copy(hovered = true)
                is HoverInteraction.Exit -> tabState = tabState.copy(hovered = false)
            }
        }
    }
    var closeButtonState by remember(isActive) { mutableStateOf(ButtonState.of(active = isActive)) }
    val lineColor by tabStyle.colors.underlineFor(tabState)
    val lineThickness = tabStyle.metrics.underlineThickness
    val backgroundColor by tabStyle.colors.backgroundFor(state = tabState)

    val resolvedContentColor = tabStyle.colors.contentFor(tabState).value.takeOrElse { LocalContentColor.current }

    CompositionLocalProvider(LocalContentColor provides resolvedContentColor) {
        Row(
            modifier
                .height(tabStyle.metrics.tabHeight)
                .background(backgroundColor)
                .focusProperties { canFocus = false }
                .selectable(
                    onClick = tabData.onClick,
                    selected = tabData.selected,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                )
                .drawBehind {
                    val strokeThickness = lineThickness.toPx()
                    val startY = size.height - (strokeThickness / 2f)
                    val endX = size.width
                    val capDxFix = strokeThickness / 2f

                    drawLine(
                        brush = SolidColor(lineColor),
                        start = Offset(0 + capDxFix, startY),
                        end = Offset(endX - capDxFix, startY),
                        strokeWidth = strokeThickness,
                        cap = StrokeCap.Round,
                    )
                }
                .padding(tabStyle.metrics.tabPadding)
                .onPointerEvent(PointerEventType.Release) { if (it.button.isTertiary) tabData.onClose() },
            horizontalArrangement = Arrangement.spacedBy(tabStyle.metrics.closeContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabData.content(TabContentScopeContainer(), tabState)

            val showCloseIcon =
                when (tabData) {
                    is TabData.Default -> tabData.closable
                    is TabData.Editor -> tabData.closable && (tabState.isHovered || tabState.isSelected)
                }

            if (showCloseIcon) {
                val closeActionInteractionSource = remember { MutableInteractionSource() }
                LaunchedEffect(closeActionInteractionSource) {
                    closeActionInteractionSource.interactions.collect { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> closeButtonState = closeButtonState.copy(pressed = true)
                            is PressInteraction.Cancel,
                            is PressInteraction.Release -> {
                                closeButtonState = closeButtonState.copy(pressed = false)
                            }

                            is HoverInteraction.Enter -> closeButtonState = closeButtonState.copy(hovered = true)

                            is HoverInteraction.Exit -> closeButtonState = closeButtonState.copy(hovered = false)
                        }
                    }
                }

                Icon(
                    key = tabStyle.icons.close,
                    modifier =
                        Modifier.clickable(
                                interactionSource = closeActionInteractionSource,
                                indication = null,
                                onClick = tabData.onClose,
                                role = Role.Button,
                            )
                            .size(16.dp),
                    contentDescription = "Close tab",
                    hint = Stateful(closeButtonState),
                )
            } else if (tabData.closable) {
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

@Immutable
@JvmInline
public value class TabState(public val state: ULong) : SelectableComponentState {
    override val isActive: Boolean
        get() = state and Active != 0UL

    override val isSelected: Boolean
        get() = state and Selected != 0UL

    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    public fun copy(
        selected: Boolean = isSelected,
        enabled: Boolean = isEnabled,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ): TabState = of(selected = selected, enabled = enabled, pressed = pressed, hovered = hovered, active = active)

    override fun toString(): String =
        "${javaClass.simpleName}(isSelected=$isSelected, isEnabled=$isEnabled, " +
            "isHovered=$isHovered, isPressed=$isPressed isActive=$isActive)"

    public companion object {
        public fun of(
            selected: Boolean,
            enabled: Boolean = true,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ): TabState =
            TabState(
                (if (selected) Selected else 0UL) or
                    (if (enabled) Enabled else 0UL) or
                    (if (pressed) Pressed else 0UL) or
                    (if (hovered) Hovered else 0UL) or
                    (if (active) Active else 0UL)
            )
    }
}
