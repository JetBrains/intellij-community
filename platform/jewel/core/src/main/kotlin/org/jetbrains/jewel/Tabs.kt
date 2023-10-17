package org.jetbrains.jewel

import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiary
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.CommonStateBitMask.Active
import org.jetbrains.jewel.CommonStateBitMask.Enabled
import org.jetbrains.jewel.CommonStateBitMask.Focused
import org.jetbrains.jewel.CommonStateBitMask.Hovered
import org.jetbrains.jewel.CommonStateBitMask.Pressed
import org.jetbrains.jewel.CommonStateBitMask.Selected
import org.jetbrains.jewel.painter.hints.Stateful

@Composable
internal fun TabImpl(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    tabData: TabData,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val tabStyle = when (tabData) {
        is TabData.Default -> IntelliJTheme.defaultTabStyle
        is TabData.Editor -> IntelliJTheme.editorTabStyle
    }

    var tabState by remember { mutableStateOf(TabState.of(selected = tabData.selected, active = isActive)) }
    remember(tabData.selected, isActive) {
        tabState = tabState.copy(selected = tabData.selected, active = isActive)
    }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> tabState = tabState.copy(pressed = true)
                is PressInteraction.Cancel, is PressInteraction.Release -> tabState = tabState.copy(pressed = false)
                is HoverInteraction.Enter -> tabState = tabState.copy(hovered = true)
                is HoverInteraction.Exit -> tabState = tabState.copy(hovered = false)
            }
        }
    }
    var closeButtonState by remember(isActive) { mutableStateOf(ButtonState.of(active = isActive)) }
    val lineColor by tabStyle.colors.underlineFor(tabState)
    val lineThickness = tabStyle.metrics.underlineThickness
    val backgroundColor by tabStyle.colors.backgroundFor(state = tabState)

    CompositionLocalProvider(
        LocalIndication provides NoIndication,
        LocalContentColor provides tabStyle.colors.contentFor(tabState).value.takeOrElse { LocalContentColor.current },
    ) {
        val labelAlpha by tabStyle.contentAlpha.labelFor(tabState)
        val iconAlpha by tabStyle.contentAlpha.iconFor(tabState)

        Row(
            modifier
                .height(tabStyle.metrics.tabHeight)
                .background(backgroundColor)
                .selectable(
                    onClick = tabData.onClick,
                    selected = tabData.selected,
                    interactionSource = interactionSource,
                    indication = NoIndication,
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
                .onPointerEvent(PointerEventType.Release) {
                    if (it.button.isTertiary) tabData.onClose()
                },
            horizontalArrangement = Arrangement.spacedBy(tabStyle.metrics.closeContentGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabData.icon?.let { icon ->
                Image(modifier = Modifier.alpha(iconAlpha), painter = icon, contentDescription = null)
            }

            Text(
                modifier = Modifier.alpha(labelAlpha),
                text = tabData.label,
                color = tabStyle.colors.contentFor(tabState).value,
            )
            val showCloseIcon = when (tabData) {
                is TabData.Default -> tabData.closable
                is TabData.Editor -> tabData.closable && (tabState.isHovered || tabState.isSelected)
            }
            if (showCloseIcon) {
                val closeActionInteractionSource = remember { MutableInteractionSource() }
                LaunchedEffect(closeActionInteractionSource) {
                    closeActionInteractionSource.interactions.collect { interaction ->
                        when (interaction) {
                            is PressInteraction.Press -> closeButtonState = closeButtonState.copy(pressed = true)
                            is PressInteraction.Cancel, is PressInteraction.Release ->
                                closeButtonState =
                                    closeButtonState.copy(pressed = false)

                            is HoverInteraction.Enter -> closeButtonState = closeButtonState.copy(hovered = true)
                            is HoverInteraction.Exit -> closeButtonState = closeButtonState.copy(hovered = false)
                        }
                    }
                }
                val closePainter by tabStyle.icons.close.getPainter(Stateful(closeButtonState))
                Image(
                    modifier = Modifier.clickable(
                        interactionSource = closeActionInteractionSource,
                        indication = null,
                        onClick = tabData.onClose,
                        role = Role.Button,
                    ).size(16.dp),
                    painter = closePainter,
                    contentDescription = "Close tab ${tabData.label}",
                )
            } else if (tabData.closable) {
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

@Immutable
@JvmInline
value class TabState(val state: ULong) : SelectableComponentState {

    @Stable
    override val isActive: Boolean
        get() = state and Active != 0UL

    @Stable
    override val isSelected: Boolean
        get() = state and Selected != 0UL

    @Stable
    override val isEnabled: Boolean
        get() = state and Enabled != 0UL

    @Stable
    override val isFocused: Boolean
        get() = state and Focused != 0UL

    @Stable
    override val isHovered: Boolean
        get() = state and Hovered != 0UL

    @Stable
    override val isPressed: Boolean
        get() = state and Pressed != 0UL

    fun copy(
        selected: Boolean = isSelected,
        enabled: Boolean = isEnabled,
        focused: Boolean = isFocused,
        pressed: Boolean = isPressed,
        hovered: Boolean = isHovered,
        active: Boolean = isActive,
    ) = of(
        selected = selected,
        enabled = enabled,
        focused = focused,
        pressed = pressed,
        hovered = hovered,
        active = active,
    )

    override fun toString() =
        "${javaClass.simpleName}(isSelected=$isSelected, isEnabled=$isEnabled, isFocused=$isFocused, " +
            "isHovered=$isHovered, isPressed=$isPressed isActive=$isActive)"

    companion object {

        fun of(
            selected: Boolean,
            enabled: Boolean = true,
            focused: Boolean = false,
            pressed: Boolean = false,
            hovered: Boolean = false,
            active: Boolean = false,
        ) = TabState(
            (if (selected) Selected else 0UL) or
                (if (enabled) Enabled else 0UL) or
                (if (focused) Focused else 0UL) or
                (if (pressed) Pressed else 0UL) or
                (if (hovered) Hovered else 0UL) or
                (if (active) Active else 0UL),
        )
    }
}
