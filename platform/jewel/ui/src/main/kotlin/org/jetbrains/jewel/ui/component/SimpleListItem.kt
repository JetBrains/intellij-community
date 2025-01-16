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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.theme.simpleListItemStyle

/**
 * A simple list item layout comprising of a content slot and an optional icon to its start side.
 *
 * The text will only take up one line and is ellipsized if too long to fit. The item will draw a background based on
 * the [isSelected] and [isActive] values.
 */
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

@GenerateDataFunctions public class ListItemState(public val isSelected: Boolean, public val isActive: Boolean = true)
