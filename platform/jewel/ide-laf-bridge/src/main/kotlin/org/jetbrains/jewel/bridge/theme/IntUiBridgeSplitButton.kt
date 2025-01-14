// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.ui.unit.dp
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.isNewUiTheme
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.SplitButtonColors
import org.jetbrains.jewel.ui.component.styling.SplitButtonMetrics
import org.jetbrains.jewel.ui.component.styling.SplitButtonStyle

private val dividerPadding: Int
    get() = if (isNewUiTheme()) 4 else 1

public fun readOutlinedSplitButtonStyle(): SplitButtonStyle =
    SplitButtonStyle(
        button = readOutlinedButtonStyle(),
        colors =
            SplitButtonColors(
                dividerColor = JBUI.CurrentTheme.Button.buttonOutlineColorStart(false).toComposeColor(),
                dividerDisabledColor = JBUI.CurrentTheme.Button.disabledOutlineColor().toComposeColor(),
                chevronColor = JBUI.CurrentTheme.Button.Split.Default.ICON_COLOR.toComposeColor(),
            ),
        metrics = SplitButtonMetrics(dividerMetrics = readDividerStyle().metrics, dividerPadding = dividerPadding.dp),
    )

public fun readDefaultSplitButtonStyle(): SplitButtonStyle =
    SplitButtonStyle(
        button = readDefaultButtonStyle(),
        colors =
            SplitButtonColors(
                dividerColor = retrieveColorOrUnspecified("Button.Split.default.separatorColor"),
                dividerDisabledColor = JBUI.CurrentTheme.Button.disabledOutlineColor().toComposeColor(),
                chevronColor = JBUI.CurrentTheme.Button.Split.Default.ICON_COLOR.toComposeColor(),
            ),
        metrics = SplitButtonMetrics(dividerMetrics = readDividerStyle().metrics, dividerPadding = dividerPadding.dp),
    )
