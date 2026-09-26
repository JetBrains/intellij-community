package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.unit.dp
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import org.jetbrains.jewel.bridge.retrieveColorOrNull
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.GroupHeaderColors
import org.jetbrains.jewel.ui.component.styling.GroupHeaderMetrics
import org.jetbrains.jewel.ui.component.styling.GroupHeaderStyle

internal fun readGroupHeaderStyle(): GroupHeaderStyle {
    val divider = retrieveColorOrNull("Group.separatorColor") ?: JBColor(Gray.xCD, Gray.x51).toComposeColor()

    return GroupHeaderStyle(
        colors =
            GroupHeaderColors(
                divider = divider,
                dividerDisabled = retrieveColorOrNull("Group.disabledSeparatorColor") ?: divider,
            ),
        metrics =
            GroupHeaderMetrics(
                dividerThickness = 1.dp, // see DarculaSeparatorUI
                indent = 1.dp, // see DarculaSeparatorUI
            ),
    )
}
