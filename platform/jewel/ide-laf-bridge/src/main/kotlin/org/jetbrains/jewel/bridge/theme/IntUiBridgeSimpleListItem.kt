package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.safeValue
import org.jetbrains.jewel.bridge.toPaddingValues
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.component.styling.SimpleListItemStyle

internal fun readSimpleListItemStyle(): SimpleListItemStyle {
    val content = retrieveColorOrUnspecified("List.foreground")

    return SimpleListItemStyle(
        colors =
            SimpleListItemColors(
                background = Color.Unspecified,
                backgroundActive = Color.Unspecified,
                backgroundSelected = retrieveColorOrUnspecified("List.selectionInactiveBackground"),
                backgroundSelectedActive = retrieveColorOrUnspecified("List.selectionBackground"),
                content = content,
                contentActive = content,
                contentSelected = content,
                contentSelectedActive = content,
            ),
        metrics =
            SimpleListItemMetrics(
                innerPadding = JBUI.CurrentTheme.PopupMenu.Selection.innerInsets().toPaddingValues(),
                outerPadding = JBUI.CurrentTheme.PopupMenu.Selection.outerInsets().toPaddingValues(),
                selectionBackgroundCornerSize =
                    CornerSize(JBUI.CurrentTheme.PopupMenu.Selection.ARC.dp.safeValue() / 2),
                iconTextGap = 2.dp,
            ),
    )
}
