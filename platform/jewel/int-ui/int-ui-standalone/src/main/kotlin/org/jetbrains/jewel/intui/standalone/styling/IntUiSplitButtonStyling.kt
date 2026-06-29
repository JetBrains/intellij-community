// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.ButtonStyle
import org.jetbrains.jewel.ui.component.styling.DividerMetrics
import org.jetbrains.jewel.ui.component.styling.SplitButtonColors
import org.jetbrains.jewel.ui.component.styling.SplitButtonMetrics
import org.jetbrains.jewel.ui.component.styling.SplitButtonStyle

/** The default [SplitButtonStyle] factory for the Int UI theme. */
public val SplitButtonStyle.Companion.Default: IntUiDefaultSplitButtonStyleFactory
    get() = IntUiDefaultSplitButtonStyleFactory

/** Factory object for creating Int UI default [SplitButtonStyle] instances. */
public object IntUiDefaultSplitButtonStyleFactory {
    /** Creates an Int UI light default [SplitButtonStyle] with the provided parameters. */
    public fun light(
        buttonStyle: ButtonStyle = ButtonStyle.Default.light(),
        dividerMetrics: DividerMetrics = DividerMetrics.defaults(),
        dividerColor: Color = IntUiLightTheme.colors.blue(8),
        dividerDisabledColor: Color = IntUiLightTheme.colors.gray(11),
        dividerPadding: Dp = 4.dp,
        chevronColor: Color = IntUiLightTheme.colors.gray(14),
    ): SplitButtonStyle =
        SplitButtonStyle(
            button = buttonStyle,
            metrics = SplitButtonMetrics(dividerMetrics, dividerPadding),
            colors = SplitButtonColors(dividerColor, dividerDisabledColor, chevronColor),
        )

    /** Creates an Int UI dark default [SplitButtonStyle] with the provided parameters. */
    public fun dark(
        buttonStyle: ButtonStyle = ButtonStyle.Default.dark(),
        dividerMetrics: DividerMetrics = DividerMetrics.defaults(),
        dividerColor: Color = IntUiDarkTheme.colors.blue(9),
        dividerDisabledColor: Color = IntUiDarkTheme.colors.gray(4),
        dividerPadding: Dp = 4.dp,
        chevronColor: Color = IntUiDarkTheme.colors.gray(14),
    ): SplitButtonStyle =
        SplitButtonStyle(
            button = buttonStyle,
            metrics = SplitButtonMetrics(dividerMetrics, dividerPadding),
            colors = SplitButtonColors(dividerColor, dividerDisabledColor, chevronColor),
        )
}

/** The outlined [SplitButtonStyle] factory for the Int UI theme. */
public val SplitButtonStyle.Companion.Outlined: IntUiOutlinedSplitButtonStyleFactory
    get() = IntUiOutlinedSplitButtonStyleFactory

/** Factory object for creating Int UI outlined [SplitButtonStyle] instances. */
public object IntUiOutlinedSplitButtonStyleFactory {
    /** Creates an Int UI light outlined [SplitButtonStyle] with the provided parameters. */
    public fun light(
        buttonStyle: ButtonStyle = ButtonStyle.Outlined.light(),
        dividerMetrics: DividerMetrics = DividerMetrics.defaults(),
        dividerColor: Color = IntUiLightTheme.colors.gray(9),
        dividerDisabledColor: Color = IntUiLightTheme.colors.gray(11),
        dividerPadding: Dp = 4.dp,
        chevronColor: Color = Color.Unspecified,
    ): SplitButtonStyle =
        SplitButtonStyle(
            button = buttonStyle,
            metrics = SplitButtonMetrics(dividerMetrics, dividerPadding),
            colors = SplitButtonColors(dividerColor, dividerDisabledColor, chevronColor),
        )

    /** Creates an Int UI dark outlined [SplitButtonStyle] with the provided parameters. */
    public fun dark(
        buttonStyle: ButtonStyle = ButtonStyle.Outlined.dark(),
        dividerMetrics: DividerMetrics = DividerMetrics.defaults(),
        dividerColor: Color = IntUiDarkTheme.colors.gray(5),
        dividerDisabledColor: Color = IntUiDarkTheme.colors.gray(4),
        dividerPadding: Dp = 4.dp,
        chevronColor: Color = IntUiDarkTheme.colors.gray(10),
    ): SplitButtonStyle =
        SplitButtonStyle(
            button = buttonStyle,
            metrics = SplitButtonMetrics(dividerMetrics, dividerPadding),
            colors = SplitButtonColors(dividerColor, dividerDisabledColor, chevronColor),
        )
}
