package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CornerSize
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveInsetsAsPaddingValues
import org.jetbrains.jewel.bridge.toPaddingValues
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle

internal fun readSimpleListItemStyle() =
    SimpleListItemStyle(
        colors =
            SimpleListItemColors(
                background = retrieveColorOrUnspecified("ComboBox.background"),
                backgroundFocused = retrieveColorOrUnspecified("ComboBox.selectionBackground"),
                backgroundSelected = retrieveColorOrUnspecified("ComboBox.selectionBackground"),
                backgroundSelectedFocused = retrieveColorOrUnspecified("ComboBox.selectionBackground"),
                content = retrieveColorOrUnspecified("ComboBox.foreground"),
                contentFocused = retrieveColorOrUnspecified("ComboBox.foreground"),
                contentSelected = retrieveColorOrUnspecified("ComboBox.foreground"),
                contentSelectedFocused = retrieveColorOrUnspecified("ComboBox.foreground"),
            ),
        metrics =
            SimpleListItemMetrics(
                innerPadding = retrieveInsetsAsPaddingValues("ComboBox.padding"),
                outerPadding = JBUI.CurrentTheme.PopupMenu.Selection.outerInsets().toPaddingValues(),
                selectionBackgroundCornerSize = CornerSize(JBUI.CurrentTheme.PopupMenu.Selection.ARC.dp / 2),
            ),
    )
