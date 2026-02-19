@file:Suppress("DEPRECATION") // This is a compat layer
@file:JvmName("TypographyKt")

package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Typography
import org.jetbrains.jewel.ui.typography

/**
 * The text style to use for regular text. Identical to
 * [org.jetbrains.jewel.foundation.theme.JewelTheme.defaultTextStyle].
 *
 * Only available when running in the IntelliJ Platform.
 */
@Deprecated(
    "Moved to org.jetbrains.jewel.ui",
    ReplaceWith(
        "JewelTheme.typography.regular",
        "org.jetbrains.jewel.ui.typography",
        "org.jetbrains.jewel.foundation.theme.JewelTheme",
    ),
)
@Composable
public fun Typography.regular(): TextStyle = JewelTheme.typography.regular

/**
 * The text style to use for medium text. Smaller than [regular].
 *
 * Only available when running in the IntelliJ Platform.
 *
 * **Note:** when using the Classic UI on Windows, this returns the same value as [regular] (the default TextStyle from
 * the theme). This is the same behavior implemented by [com.intellij.util.ui.JBFont].
 */
@Deprecated(
    "Moved to org.jetbrains.jewel.ui",
    ReplaceWith(
        "JewelTheme.typography.medium",
        "org.jetbrains.jewel.ui.typography",
        "org.jetbrains.jewel.foundation.theme.JewelTheme",
    ),
)
@Composable
public fun Typography.medium(): TextStyle = JewelTheme.typography.medium

/**
 * The text style to use for small text. Smaller than [medium]. Should be avoided when running in the New UI, in favor
 * of [medium], unless it's absolutely necessary.
 *
 * Only available when running in the IntelliJ Platform.
 *
 * **Note:** when using the Classic UI on Windows, this returns the same value as [regular] (the default TextStyle from
 * the theme). This is the same behavior implemented by [com.intellij.util.ui.JBFont].
 */
@Deprecated(
    "Moved to org.jetbrains.jewel.ui",
    ReplaceWith(
        "JewelTheme.typography.small",
        "org.jetbrains.jewel.ui.typography",
        "org.jetbrains.jewel.foundation.theme.JewelTheme",
    ),
)
@Composable
public fun Typography.small(): TextStyle = JewelTheme.typography.small
