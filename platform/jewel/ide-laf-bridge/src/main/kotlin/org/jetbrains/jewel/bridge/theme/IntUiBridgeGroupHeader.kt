package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.GroupHeaderColors
import org.jetbrains.jewel.ui.component.styling.GroupHeaderMetrics
import org.jetbrains.jewel.ui.component.styling.GroupHeaderStyle

internal fun readGroupHeaderStyle() =
    GroupHeaderStyle(
        colors = GroupHeaderColors(divider = retrieveColorOrUnspecified("Separator.separatorColor")),
        metrics =
            GroupHeaderMetrics(
                dividerThickness = 1.dp, // see DarculaSeparatorUI
                indent = 1.dp, // see DarculaSeparatorUI
            ),
    )
