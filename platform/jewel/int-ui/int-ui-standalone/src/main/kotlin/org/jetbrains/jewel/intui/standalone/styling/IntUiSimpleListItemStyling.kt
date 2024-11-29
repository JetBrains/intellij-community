package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle

@Composable
public fun SimpleListItemStyle.Companion.default(): SimpleListItemStyle =
    if (isSystemInDarkTheme()) {
        dark()
    } else {
        light()
    }

@Composable
public fun SimpleListItemStyle.Companion.fullWidth(): SimpleListItemStyle =
    if (isSystemInDarkTheme()) {
        darkFullWidth()
    } else {
        lightFullWidth()
    }

public fun SimpleListItemStyle.Companion.lightFullWidth(
    background: Color = Color.Unspecified,
    backgroundFocused: Color = IntUiLightTheme.colors.blue(11),
    backgroundSelected: Color = IntUiLightTheme.colors.blue(11),
    backgroundSelectedFocused: Color = IntUiLightTheme.colors.blue(11),
    content: Color = Color.Unspecified,
    contentFocused: Color = Color.Unspecified,
    contentSelected: Color = Color.Unspecified,
    contentSelectedFocused: Color = Color.Unspecified,
): SimpleListItemStyle =
    SimpleListItemStyle(
        SimpleListItemColors(
            background = background,
            backgroundFocused = backgroundFocused,
            backgroundSelected = backgroundSelected,
            backgroundSelectedFocused = backgroundSelectedFocused,
            content = content,
            contentFocused = contentFocused,
            contentSelected = contentSelected,
            contentSelectedFocused = contentSelectedFocused,
        ),
        SimpleListItemMetrics(
            innerPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            outerPadding = PaddingValues(),
            selectionBackgroundCornerSize = CornerSize(0.dp),
        ),
    )

public fun SimpleListItemStyle.Companion.darkFullWidth(
    background: Color = Color.Unspecified,
    backgroundFocused: Color = IntUiLightTheme.colors.blue(2),
    backgroundSelected: Color = IntUiLightTheme.colors.blue(2),
    backgroundSelectedFocused: Color = IntUiLightTheme.colors.blue(2),
    content: Color = Color.Unspecified,
    contentFocused: Color = Color.Unspecified,
    contentSelected: Color = Color.Unspecified,
    contentSelectedFocused: Color = Color.Unspecified,
): SimpleListItemStyle =
    SimpleListItemStyle(
        SimpleListItemColors(
            background = background,
            backgroundFocused = backgroundFocused,
            backgroundSelected = backgroundSelected,
            backgroundSelectedFocused = backgroundSelectedFocused,
            content = content,
            contentFocused = contentFocused,
            contentSelected = contentSelected,
            contentSelectedFocused = contentSelectedFocused,
        ),
        SimpleListItemMetrics(
            innerPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            outerPadding = PaddingValues(),
            selectionBackgroundCornerSize = CornerSize(0.dp),
        ),
    )

public fun SimpleListItemStyle.Companion.light(): SimpleListItemStyle =
    SimpleListItemStyle(
        SimpleListItemStyle.lightFullWidth().colors,
        SimpleListItemMetrics(
            innerPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            outerPadding = PaddingValues(horizontal = 7.dp, vertical = 1.dp),
            selectionBackgroundCornerSize = CornerSize(4.dp),
        ),
    )

public fun SimpleListItemStyle.Companion.dark(): SimpleListItemStyle =
    SimpleListItemStyle(
        SimpleListItemStyle.darkFullWidth().colors,
        SimpleListItemMetrics(
            innerPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            outerPadding = PaddingValues(horizontal = 7.dp, vertical = 1.dp),
            selectionBackgroundCornerSize = CornerSize(4.dp),
        ),
    )
