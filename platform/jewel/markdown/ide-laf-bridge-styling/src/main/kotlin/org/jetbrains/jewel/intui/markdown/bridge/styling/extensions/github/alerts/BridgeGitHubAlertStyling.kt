// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.intui.markdown.bridge.styling.extensions.github.alerts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.intui.markdown.bridge.styling.isLightTheme
import org.jetbrains.jewel.markdown.extensions.github.alerts.AlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.CautionAlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.GitHubAlertIcons
import org.jetbrains.jewel.markdown.extensions.github.alerts.ImportantAlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.NoteAlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.TipAlertStyling
import org.jetbrains.jewel.markdown.extensions.github.alerts.WarningAlertStyling
import org.jetbrains.jewel.ui.icon.IconKey

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun AlertStyling.Companion.create(
    note: NoteAlertStyling = NoteAlertStyling.create(),
    tip: TipAlertStyling = TipAlertStyling.create(),
    important: ImportantAlertStyling = ImportantAlertStyling.create(),
    warning: WarningAlertStyling = WarningAlertStyling.create(),
    caution: CautionAlertStyling = CautionAlertStyling.create(),
): AlertStyling = AlertStyling(note, tip, important, warning, caution)

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun NoteAlertStyling.Companion.create(
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    lineWidth: Dp = 3.dp,
    lineColor: Color = if (isLightTheme) Color(0xFF0969DA) else Color(0xFF1F6EEB),
    pathEffect: PathEffect? = null,
    strokeCap: StrokeCap = StrokeCap.Square,
    titleTextStyle: TextStyle = TextStyle(fontWeight = FontWeight.Medium, color = lineColor),
    titleIconKey: IconKey? = GitHubAlertIcons.Note,
    titleIconTint: Color = lineColor,
    textColor: Color = Color.Unspecified,
): NoteAlertStyling =
    NoteAlertStyling(
        padding,
        lineWidth,
        lineColor,
        pathEffect,
        strokeCap,
        titleTextStyle,
        titleIconKey,
        titleIconTint,
        textColor,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun TipAlertStyling.Companion.create(
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    lineWidth: Dp = 3.dp,
    lineColor: Color = if (isLightTheme) Color(0xFF1F883D) else Color(0xFF238636),
    pathEffect: PathEffect? = null,
    strokeCap: StrokeCap = StrokeCap.Square,
    titleTextStyle: TextStyle = TextStyle(fontWeight = FontWeight.Medium, color = lineColor),
    titleIconKey: IconKey? = GitHubAlertIcons.Tip,
    titleIconTint: Color = lineColor,
    textColor: Color = Color.Unspecified,
): TipAlertStyling =
    TipAlertStyling(
        padding,
        lineWidth,
        lineColor,
        pathEffect,
        strokeCap,
        titleTextStyle,
        titleIconKey,
        titleIconTint,
        textColor,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun ImportantAlertStyling.Companion.create(
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    lineWidth: Dp = 3.dp,
    lineColor: Color = if (isLightTheme) Color(0xFF8250DF) else Color(0xFF8957E5),
    pathEffect: PathEffect? = null,
    strokeCap: StrokeCap = StrokeCap.Square,
    titleTextStyle: TextStyle = TextStyle(fontWeight = FontWeight.Medium, color = lineColor),
    titleIconKey: IconKey? = GitHubAlertIcons.Important,
    titleIconTint: Color = lineColor,
    textColor: Color = Color.Unspecified,
): ImportantAlertStyling =
    ImportantAlertStyling(
        padding,
        lineWidth,
        lineColor,
        pathEffect,
        strokeCap,
        titleTextStyle,
        titleIconKey,
        titleIconTint,
        textColor,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun WarningAlertStyling.Companion.create(
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    lineWidth: Dp = 3.dp,
    lineColor: Color = if (isLightTheme) Color(0xFF9A6601) else Color(0xFF9E6A02),
    pathEffect: PathEffect? = null,
    strokeCap: StrokeCap = StrokeCap.Square,
    titleTextStyle: TextStyle = TextStyle(fontWeight = FontWeight.Medium, color = lineColor),
    titleIconKey: IconKey? = GitHubAlertIcons.Warning,
    titleIconTint: Color = lineColor,
    textColor: Color = Color.Unspecified,
): WarningAlertStyling =
    WarningAlertStyling(
        padding,
        lineWidth,
        lineColor,
        pathEffect,
        strokeCap,
        titleTextStyle,
        titleIconKey,
        titleIconTint,
        textColor,
    )

@ApiStatus.Experimental
@ExperimentalJewelApi
public fun CautionAlertStyling.Companion.create(
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    lineWidth: Dp = 3.dp,
    lineColor: Color = if (isLightTheme) Color(0xFFCF222E) else Color(0xFFDA3633),
    pathEffect: PathEffect? = null,
    strokeCap: StrokeCap = StrokeCap.Square,
    titleTextStyle: TextStyle = TextStyle(fontWeight = FontWeight.Medium, color = lineColor),
    titleIconKey: IconKey? = GitHubAlertIcons.Caution,
    titleIconTint: Color = lineColor,
    textColor: Color = Color.Unspecified,
): CautionAlertStyling =
    CautionAlertStyling(
        padding,
        lineWidth,
        lineColor,
        pathEffect,
        strokeCap,
        titleTextStyle,
        titleIconKey,
        titleIconTint,
        textColor,
    )
