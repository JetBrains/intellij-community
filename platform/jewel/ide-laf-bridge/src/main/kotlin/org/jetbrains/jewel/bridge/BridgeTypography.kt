// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.asComposeFontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.util.ui.JBFont
import java.awt.Font
import org.jetbrains.jewel.bridge.theme.computeBaseLineHeightFor
import org.jetbrains.jewel.bridge.theme.retrieveDefaultTextStyle
import org.jetbrains.jewel.bridge.theme.retrieveEditorTextStyle
import org.jetbrains.jewel.ui.Typography

/**
 * An implementation of [Typography] that obtains typography information from the IntelliJ Platform LaF. It uses
 * [JBFont] as a reference for font sizes and style.
 */
public object BridgeTypography : Typography {
    @get:Composable
    override val labelTextStyle: TextStyle
        get() = retrieveDefaultTextStyle()

    @get:Composable
    override val labelTextSize: TextUnit
        get() = labelTextStyle.fontSize

    @get:Composable
    override val h0TextStyle: TextStyle
        get() = labelTextStyle.applyFrom(JBFont.h0())

    @get:Composable
    override val h1TextStyle: TextStyle
        get() = labelTextStyle.applyFrom(JBFont.h1())

    @get:Composable
    override val h2TextStyle: TextStyle
        get() = labelTextStyle.applyFrom(JBFont.h2())

    @get:Composable
    override val h3TextStyle: TextStyle
        get() = labelTextStyle.applyFrom(JBFont.h3())

    @get:Composable
    override val h4TextStyle: TextStyle
        get() = labelTextStyle.applyFrom(JBFont.h4())

    @get:Composable
    override val editorTextStyle: TextStyle
        get() = retrieveEditorTextStyle()

    @get:Composable
    override val consoleTextStyle: TextStyle
        get() = retrieveEditorTextStyle()

    @get:Composable
    override val regular: TextStyle
        get() = labelTextStyle

    @get:Composable
    override val medium: TextStyle
        get() = labelTextStyle.applyFrom(JBFont.medium())

    @get:Composable
    override val small: TextStyle
        get() = labelTextStyle.applyFrom(JBFont.small())
}

@OptIn(ExperimentalTextApi::class)
private fun TextStyle.applyFrom(font: Font) =
    copy(
        fontFamily = font.asComposeFontFamily(),
        fontSize = font.sizeSp,
        fontWeight = if (font.isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (font.isItalic) FontStyle.Italic else FontStyle.Normal,
        lineHeight = computeBaseLineHeightFor(font).sp,
    )

private val Font.sizeSp
    get() = size.sp / UISettingsUtils.getInstance().currentIdeScale
