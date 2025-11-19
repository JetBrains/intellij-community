// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.ui.component.styling.TableCellColors
import org.jetbrains.jewel.ui.component.styling.TableColors
import org.jetbrains.jewel.ui.component.styling.TableMetrics
import org.jetbrains.jewel.ui.component.styling.TableStyle

internal fun readTableStyle() =
    TableStyle(
        colors = TableColors(cell = readTableCellColors(), header = readTableHeaderCellColors()),
        metrics = TableMetrics(1.dp, 1.dp),
    )

private fun readTableCellColors(): TableCellColors =
    TableCellColors(
        background = retrieveColorOrUnspecified("Table.background"),
        backgroundSelected = retrieveColorOrUnspecified("Table.selectionBackground"),
        backgroundStripe = retrieveColorOrUnspecified("Table.stripeColor"),
        foreground = retrieveColorOrUnspecified("Table.foreground"),
        foregroundSelected = retrieveColorOrUnspecified("Table.selectionForeground"),
        foregroundStripe = retrieveColorOrUnspecified("Table.foreground"),
        borderColor = retrieveColorOrUnspecified("Table.gridColor"),
    )

private fun readTableHeaderCellColors(): TableCellColors =
    TableCellColors(
        background = retrieveColorOrUnspecified("TableHeader.background"),
        foreground = retrieveColorOrUnspecified("TableHeader.foreground"),
        borderColor = retrieveColorOrUnspecified("TableHeader.separatorColor"),
    )
