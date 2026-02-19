package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.styling.SelectableLazyColumnStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle

public fun SelectableLazyColumnStyle.Companion.light(
    itemHeight: Dp = 24.dp,
    itemColors: SimpleListItemColors = SimpleListItemColors.light(),
    itemMetrics: SimpleListItemMetrics = SimpleListItemMetrics.default(),
): SelectableLazyColumnStyle = SelectableLazyColumnStyle(itemHeight, SimpleListItemStyle(itemColors, itemMetrics))

public fun SelectableLazyColumnStyle.Companion.dark(
    itemHeight: Dp = 24.dp,
    itemColors: SimpleListItemColors = SimpleListItemColors.dark(),
    itemMetrics: SimpleListItemMetrics = SimpleListItemMetrics.default(),
): SelectableLazyColumnStyle = SelectableLazyColumnStyle(itemHeight, SimpleListItemStyle(itemColors, itemMetrics))
