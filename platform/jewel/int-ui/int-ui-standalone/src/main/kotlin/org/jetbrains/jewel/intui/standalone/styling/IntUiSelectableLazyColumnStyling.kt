package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.SelectableLazyColumnStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle

@Composable
public fun SelectableLazyColumnStyle.Companion.light(
    itemHeight: Dp = 24.dp,
    selectionBackgroundColor: Color = IntUiLightTheme.colors.blue(11),
    selectionBackgroundCornerRadius: CornerSize = CornerSize(0.dp),
    itemContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    itemPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    contentColor: Color = Color.Unspecified,
    selectedContentColor: Color = Color.Unspecified,
): SelectableLazyColumnStyle =
    SelectableLazyColumnStyle(
        itemHeight,
        SimpleListItemStyle(
            SimpleListItemColors(
                background = selectionBackgroundColor,
                backgroundFocused = selectionBackgroundColor,
                backgroundSelected = selectionBackgroundColor,
                backgroundSelectedFocused = selectionBackgroundColor,
                content = contentColor,
                contentFocused = contentColor,
                contentSelected = contentColor,
                contentSelectedFocused = selectedContentColor,
            ),
            SimpleListItemMetrics(
                innerPadding = itemContentPadding,
                outerPadding = itemPadding,
                selectionBackgroundCornerSize = selectionBackgroundCornerRadius,
            ),
        ),
    )

@Composable
public fun SelectableLazyColumnStyle.Companion.dark(
    itemHeight: Dp = 24.dp,
    selectionBackgroundColor: Color = IntUiLightTheme.colors.blue(2),
    selectionBackgroundCornerRadius: CornerSize = CornerSize(0.dp),
    itemContentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    itemPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    contentColor: Color = Color.Unspecified,
    selectedContentColor: Color = Color.Unspecified,
): SelectableLazyColumnStyle =
    SelectableLazyColumnStyle(
        itemHeight,
        SimpleListItemStyle(
            SimpleListItemColors(
                background = selectionBackgroundColor,
                backgroundFocused = selectionBackgroundColor,
                backgroundSelected = selectionBackgroundColor,
                backgroundSelectedFocused = selectionBackgroundColor,
                content = contentColor,
                contentFocused = contentColor,
                contentSelected = contentColor,
                contentSelectedFocused = selectedContentColor,
            ),
            SimpleListItemMetrics(
                innerPadding = itemContentPadding,
                outerPadding = itemPadding,
                selectionBackgroundCornerSize = selectionBackgroundCornerRadius,
            ),
        ),
    )
