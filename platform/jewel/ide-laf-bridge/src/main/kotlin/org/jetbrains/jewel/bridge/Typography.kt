package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.NewUiValue
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.component.minus

/**
 * The text style to use for regular text. Identical to
 * [JewelTheme.textStyle].
 *
 * Only available when running in the IntelliJ Platform.
 */
@Composable
public fun Typography.regular(): TextStyle = labelTextStyle()

/**
 * The text style to use for medium text. Smaller than [regular].
 *
 * Only available when running in the IntelliJ Platform.
 *
 * **Note:** when using the Classic UI on Windows, this returns the same
 * value as [regular] (the default TextStyle from the theme). This is the
 * same behavior implemented by [com.intellij.util.ui.JBFont].
 */
@Composable
public fun Typography.medium(): TextStyle =
    if (mediumAndSmallFontsAsRegular()) {
        labelTextStyle()
    } else {
        labelTextStyle().copy(fontSize = labelTextSize() - 1.sp)
    }

/**
 * The text style to use for small text. Smaller than [medium]. Should be
 * avoided when running in the New UI, in favor of [medium], unless it's
 * absolutely necessary.
 *
 * Only available when running in the IntelliJ Platform.
 *
 * **Note:** when using the Classic UI on Windows, this returns the same
 * value as [regular] (the default TextStyle from the theme). This is the
 * same behavior implemented by [com.intellij.util.ui.JBFont].
 */
@Composable
public fun Typography.small(): TextStyle =
    if (mediumAndSmallFontsAsRegular()) {
        labelTextStyle()
    } else {
        labelTextStyle().copy(fontSize = labelTextSize() - 2.sp)
    }

// Copied from JBFont — current as of IJP 233.
private fun mediumAndSmallFontsAsRegular(): Boolean =
    SystemInfo.isWindows && !NewUiValue.isEnabled()
