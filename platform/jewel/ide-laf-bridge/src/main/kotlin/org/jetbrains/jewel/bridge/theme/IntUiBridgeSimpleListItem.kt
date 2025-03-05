package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.toPaddingValues
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle

internal fun readSimpleListItemStyle() =
    SimpleListItemStyle(
        colors =
            SimpleListItemColors(
                background = retrieveColorOrUnspecified("ComboBox.background"),
                backgroundActive = retrieveColorOrUnspecified("ComboBox.background"),
                backgroundSelected = retrieveColorOrUnspecified("ComboBox.selectionBackground"),
                backgroundSelectedActive = retrieveColorOrUnspecified("ComboBox.selectionBackground"),
                content = retrieveColorOrUnspecified("ComboBox.foreground"),
                contentActive = retrieveColorOrUnspecified("ComboBox.foreground"),
                contentSelected = retrieveColorOrUnspecified("ComboBox.foreground"),
                contentSelectedActive = retrieveColorOrUnspecified("ComboBox.foreground"),
            ),
        metrics =
            SimpleListItemMetrics(
                innerPadding = JBUI.CurrentTheme.PopupMenu.Selection.innerInsets().toPaddingValues(),
                outerPadding = JBUI.CurrentTheme.PopupMenu.Selection.outerInsets().toPaddingValues(),
                selectionBackgroundCornerSize = CornerSize(JBUI.CurrentTheme.PopupMenu.Selection.ARC.dp / 2),
                iconTextGap = 2.dp,
            ),
    )
