package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.theme.simpleListItemStyle

/**
 * A simple list item layout comprising of a text and an optional icon to its start side.
 *
 * The text will only take up one line and is ellipsized if too long to fit.
 *
 * @param text The text displayed in the list item
 * @param state The state of the list item, containing selection and activity status.
 * @param modifier Optional [Modifier] to apply to to the list item.
 * @param textModifier Optional [Modifier] to apply to to the list item text.
 * @param iconModifier Optional [Modifier] to apply to specifically to the icon.
 * @param icon Optional [IconKey] representing the icon displayed in the list item.
 * @param iconContentDescription Optional content description [String] for accessibility purposes for the icon.
 * @param style Optional [SimpleListItemStyle] for defining the appearance of the list item; default is based on the
 *   Jewel theme.
 * @param height The height of the list item; default is based on the Jewel theme's global metrics.
 * @param colorFilter Optional [ColorFilter] to apply to the icon, if any.
 * @param painterHints Optional [PainterHint]s to apply to the icon, if any.
 */
@Composable
public fun SimpleListItem(
    text: String,
    state: ListItemState,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    icon: IconKey? = null,
    iconContentDescription: String? = null,
    style: SimpleListItemStyle = JewelTheme.simpleListItemStyle,
    height: Dp = JewelTheme.globalMetrics.rowHeight,
    colorFilter: ColorFilter? = null,
    vararg painterHints: PainterHint,
) {
    SimpleListItem(
        state = state,
        modifier = modifier,
        iconModifier = iconModifier,
        icon = icon,
        iconContentDescription = iconContentDescription,
        style = style,
        height = height,
        painterHints = painterHints.toList(),
        colorFilter = colorFilter,
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = JewelTheme.defaultTextStyle,
            color = style.colors.contentFor(state).value,
            modifier = textModifier,
        )
    }
}

/**
 * A simple list item layout comprising of a text and an optional icon to its start side.
 *
 * The text will only take up one line and is ellipsized if too long to fit. It also exposes [selected] and [active]
 * properties, instead of the state.
 *
 * @param text The text displayed in the list item.
 * @param selected Indicates whether the list item is selected.
 * @param active Indicates whether the list item is active or disabled; default is active.
 * @param modifier Optional [Modifier] to apply to to the entire list item.
 * @param textModifier Optional [Modifier] to apply to specifically to the text.
 * @param iconModifier Optional [Modifier] to apply to specifically to the icon.
 * @param icon Optional [IconKey] representing the icon displayed on the start side of the list item.
 * @param iconContentDescription Optional content description [String] for the icon for accessibility purposes.
 * @param style The [SimpleListItemStyle] defining the appearance of the list item; default is based on the Jewel theme.
 * @param height The height of the list item; default is based on the Jewel theme's global metrics.
 * @param colorFilter Optional [ColorFilter] to apply to the icon, if any.
 * @param painterHints Optional [PainterHint]s to apply to the icon, if any.
 */
@Composable
public fun SimpleListItem(
    text: String,
    selected: Boolean,
    active: Boolean = true,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    icon: IconKey? = null,
    iconContentDescription: String? = null,
    style: SimpleListItemStyle = JewelTheme.simpleListItemStyle,
    height: Dp = JewelTheme.globalMetrics.rowHeight,
    colorFilter: ColorFilter? = null,
    vararg painterHints: PainterHint,
) {
    val state = remember(selected, active) { ListItemState(selected, active) }
    SimpleListItem(
        text = text,
        state = state,
        modifier = modifier,
        textModifier = textModifier,
        iconModifier = iconModifier,
        icon = icon,
        iconContentDescription = iconContentDescription,
        style = style,
        height = height,
        colorFilter = colorFilter,
        painterHints = painterHints,
    )
}

/**
 * A simple list item layout comprising of a content slot and an optional icon to its start side. It also exposes
 * [selected] and [active] properties, instead of the state.
 *
 * @param selected Indicates whether the list item is selected.
 * @param active Determines if the list item is in an active state (e.g., enabled or interactive).
 * @param colorFilter Optional [ColorFilter] applied to the item, typically used with the icon.
 * @param painterHints Optional list of [PainterHint] to provide hints for painting customizations; default is empty.
 * @param modifier Optional [Modifier] to apply to to the list item.
 * @param iconModifier Optional [Modifier] to apply to specifically to the icon.
 * @param icon Optional [IconKey] representing the icon displayed in the list item.
 * @param iconContentDescription Optional content description [String] for accessibility purposes for the icon.
 * @param style Optional [SimpleListItemStyle] for defining the appearance of the list item; default is based on the
 *   Jewel theme.
 * @param colorFilter Optional [ColorFilter] to apply to the icon, if any.
 * @param painterHints Optional [PainterHint]s to apply to the icon, if any.
 */
@Composable
public fun SimpleListItem(
    selected: Boolean,
    active: Boolean = true,
    colorFilter: ColorFilter? = null,
    painterHints: List<PainterHint> = emptyList(),
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    icon: IconKey? = null,
    iconContentDescription: String? = null,
    style: SimpleListItemStyle = JewelTheme.simpleListItemStyle,
    height: Dp = JewelTheme.globalMetrics.rowHeight,
    content: @Composable () -> Unit,
) {
    val state = remember(selected, active) { ListItemState(selected, active) }
    SimpleListItem(
        state = state,
        modifier = modifier,
        iconModifier = iconModifier,
        icon = icon,
        iconContentDescription = iconContentDescription,
        style = style,
        height = height,
        colorFilter = colorFilter,
        painterHints = painterHints,
        content = content,
    )
}

/**
 * A simple list item layout comprising of a content slot and an optional icon to its start side.
 *
 * @param state The state of the list item, containing selection and activity status.
 * @param colorFilter Optional [ColorFilter] applied to the item, typically used with the icon.
 * @param painterHints Optional list of [PainterHint] to provide hints for painting customizations; default is empty.
 * @param modifier Optional [Modifier] to apply to the root container of the list item.
 * @param iconModifier Optional [Modifier] to apply to the icon displayed in the list item.
 * @param icon Optional [IconKey] that defines which icon should be displayed.
 * @param iconContentDescription Optional content description [String] for the icon, used for accessibility.
 * @param style The style of the list item, including colors and metrics, with a default value from the theme; default
 *   is based on the Jewel theme.
 * @param colorFilter Optional [ColorFilter] to apply to the icon, if any.
 * @param painterHints Optional [PainterHint]s to apply to the icon, if any.
 */
@Composable
public fun SimpleListItem(
    state: ListItemState,
    colorFilter: ColorFilter? = null,
    painterHints: List<PainterHint> = emptyList(),
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    icon: IconKey? = null,
    iconContentDescription: String? = null,
    style: SimpleListItemStyle = JewelTheme.simpleListItemStyle,
    height: Dp = JewelTheme.globalMetrics.rowHeight,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .semantics { selected = state.isSelected }
                .fillMaxWidth()
                .height(height)
                .padding(style.metrics.outerPadding)
                .background(
                    color = style.colors.backgroundFor(state).value,
                    shape = RoundedCornerShape(style.metrics.selectionBackgroundCornerSize),
                )
                .padding(style.metrics.innerPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(style.metrics.iconTextGap),
    ) {
        if (icon != null) {
            Icon(
                modifier = iconModifier.size(16.dp),
                key = icon,
                contentDescription = iconContentDescription,
                colorFilter = colorFilter,
                hints = painterHints.toTypedArray(),
            )
        }
        content()
    }
}

/**
 * A simple list item layout comprising of a content slot and an optional icon to its start side.
 *
 * The text will only take up one line and is ellipsized if too long to fit. The item will draw a background based on
 * the [isSelected] and [isActive] values.
 */
@Deprecated("Use the overload with selected, active, colorFilter and hints")
@ScheduledForRemoval(inVersion = "In 2025.3")
@Composable
public fun SimpleListItem(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    isActive: Boolean = true,
    icon: IconKey? = null,
    iconContentDescription: String? = null,
    style: SimpleListItemStyle = JewelTheme.simpleListItemStyle,
    height: Dp = JewelTheme.globalMetrics.rowHeight,
    content: @Composable () -> Unit,
) {
    val state = remember(isSelected, isActive) { ListItemState(isSelected, isActive) }
    SimpleListItem(state, modifier, iconModifier, icon, iconContentDescription, style, height, content)
}

/**
 * A simple list item layout comprising of a text and an optional icon to its start side.
 *
 * The text will only take up one line and is ellipsized if too long to fit. The item will draw a background based on
 * the [isSelected] and [isActive] values.
 */
@Deprecated("Use the overload with selected, active, colorFilter and hints")
@ScheduledForRemoval(inVersion = "In 2025.3")
@Composable
public fun SimpleListItem(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    isActive: Boolean = true,
    icon: IconKey? = null,
    iconContentDescription: String? = null,
    style: SimpleListItemStyle = JewelTheme.simpleListItemStyle,
    height: Dp = JewelTheme.globalMetrics.rowHeight,
) {
    val state = remember(isSelected, isActive) { ListItemState(isSelected, isActive) }
    SimpleListItem(text, state, modifier, textModifier, iconModifier, icon, iconContentDescription, style, height)
}

/**
 * A simple list item layout comprising of a text and an optional icon to its start side.
 *
 * The text will only take up one line and is ellipsized if too long to fit. The item will draw a background based on
 * the [state].
 */
@Deprecated("Use the overload with selected, active, colorFilter and hints")
@ScheduledForRemoval(inVersion = "In 2025.3")
@Composable
public fun SimpleListItem(
    text: String,
    state: ListItemState,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    icon: IconKey? = null,
    iconContentDescription: String? = null,
    style: SimpleListItemStyle = JewelTheme.simpleListItemStyle,
    height: Dp = JewelTheme.globalMetrics.rowHeight,
) {
    SimpleListItem(state, modifier, iconModifier, icon, iconContentDescription, style, height) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = JewelTheme.defaultTextStyle,
            color = style.colors.contentFor(state).value,
            modifier = textModifier,
        )
    }
}

/**
 * A simple list item layout comprising of a content slot and an optional icon to its start side.
 *
 * The text will only take up one line and is ellipsized if too long to fit. The item will draw a background based on
 * the [state].
 */
@Deprecated("Use the overload with selected, active, colorFilter and hints")
@ScheduledForRemoval(inVersion = "In 2025.3")
@Composable
public fun SimpleListItem(
    state: ListItemState,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    icon: IconKey? = null,
    iconContentDescription: String? = null,
    style: SimpleListItemStyle = JewelTheme.simpleListItemStyle,
    height: Dp = JewelTheme.globalMetrics.rowHeight,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .semantics { selected = state.isSelected }
                .fillMaxWidth()
                .height(height)
                .padding(style.metrics.outerPadding)
                .background(
                    color = style.colors.backgroundFor(state).value,
                    shape = RoundedCornerShape(style.metrics.selectionBackgroundCornerSize),
                )
                .padding(style.metrics.innerPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(style.metrics.iconTextGap),
    ) {
        if (icon != null) {
            Icon(modifier = iconModifier.size(16.dp), key = icon, contentDescription = iconContentDescription)
        }
        content()
    }
}

@GenerateDataFunctions
public class ListItemState(public val isSelected: Boolean, public val isActive: Boolean = true) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ListItemState

        if (isSelected != other.isSelected) return false
        if (isActive != other.isActive) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isSelected.hashCode()
        result = 31 * result + isActive.hashCode()
        return result
    }

    override fun toString(): String = "ListItemState(isSelected=$isSelected, isActive=$isActive)"
}
