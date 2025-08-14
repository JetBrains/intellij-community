// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.DefaultFontSize
import org.jetbrains.jewel.intui.standalone.theme.createDefaultTextStyle
import org.jetbrains.jewel.intui.standalone.theme.createEditorTextStyle
import org.jetbrains.jewel.ui.Typography
import org.jetbrains.jewel.ui.component.minus
import org.jetbrains.jewel.ui.component.plus

/** An implementation of [Typography] that uses default Int UI typography information. */
public object IntUiTypography : Typography {
    @get:Composable
    override val labelTextStyle: TextStyle
        get() = JewelTheme.createDefaultTextStyle()

    @get:Composable
    override val labelTextSize: TextUnit
        get() = DefaultFontSize

    @get:Composable
    override val h0TextStyle: TextStyle
        get() = JewelTheme.createDefaultTextStyle(fontSize = DefaultFontSize + 12.sp, fontWeight = FontWeight.Bold)

    @get:Composable
    override val h1TextStyle: TextStyle
        get() = JewelTheme.createDefaultTextStyle(fontSize = DefaultFontSize + 9.sp, fontWeight = FontWeight.Bold)

    @get:Composable
    override val h2TextStyle: TextStyle
        get() = JewelTheme.createDefaultTextStyle(fontSize = DefaultFontSize + 5.sp)

    @get:Composable
    override val h3TextStyle: TextStyle
        get() = JewelTheme.createDefaultTextStyle(fontSize = DefaultFontSize + 3.sp)

    @get:Composable
    override val h4TextStyle: TextStyle
        get() = JewelTheme.createDefaultTextStyle(fontSize = DefaultFontSize + 1.sp, fontWeight = FontWeight.Bold)

    @get:Composable
    override val editorTextStyle: TextStyle
        get() = JewelTheme.createEditorTextStyle()

    @get:Composable
    override val consoleTextStyle: TextStyle
        get() = editorTextStyle

    @get:Composable
    override val regular: TextStyle
        get() = labelTextStyle

    @get:Composable
    override val medium: TextStyle
        get() = JewelTheme.createDefaultTextStyle(fontSize = DefaultFontSize - 1.sp)

    @get:Composable
    override val small: TextStyle
        get() = JewelTheme.createDefaultTextStyle(fontSize = DefaultFontSize - 2.sp)
}
