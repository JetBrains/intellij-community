// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.jetbrains.jewel.bridge.theme.retrieveConsoleTextStyle
import org.jetbrains.jewel.bridge.theme.retrieveDefaultTextStyle
import org.jetbrains.jewel.bridge.theme.retrieveEditorTextStyle
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Typography

/**
 * An implementation of [Typography] that obtains typography information from the IntelliJ Platform LaF. It uses
 * [JBFont] as a reference for font sizes and style.
 */
public object BridgeTypography : Typography {
    @get:Composable
    override val labelTextStyle: TextStyle
        get() = remember(JewelTheme.instanceUuid) { retrieveDefaultTextStyle() }

    @get:Composable
    override val labelTextSize: TextUnit
        get() {
            val baseStyle = labelTextStyle
            return remember(baseStyle) { mutableStateOf(baseStyle.fontSize) }.value
        }

    @get:Composable
    override val h0TextStyle: TextStyle
        get() {
            val baseStyle = labelTextStyle
            return remember(baseStyle) { mutableStateOf(baseStyle.applyFrom(JBFont.h0())) }.value
        }

    @get:Composable
    override val h1TextStyle: TextStyle
        get() {
            val baseStyle = labelTextStyle
            return remember(baseStyle) { mutableStateOf(baseStyle.applyFrom(JBFont.h1())) }.value
        }

    @get:Composable
    override val h2TextStyle: TextStyle
        get() {
            val baseStyle = labelTextStyle
            return remember(baseStyle) { mutableStateOf(baseStyle.applyFrom(JBFont.h2())) }.value
        }

    @get:Composable
    override val h3TextStyle: TextStyle
        get() {
            val baseStyle = labelTextStyle
            return remember(baseStyle) { mutableStateOf(baseStyle.applyFrom(JBFont.h3())) }.value
        }

    @get:Composable
    override val h4TextStyle: TextStyle
        get() {
            val baseStyle = labelTextStyle
            return remember(baseStyle) { mutableStateOf(baseStyle.applyFrom(JBFont.h4())) }.value
        }

    @get:Composable
    override val editorTextStyle: TextStyle
        get() = remember(JewelTheme.instanceUuid) { retrieveEditorTextStyle() }

    @get:Composable
    override val consoleTextStyle: TextStyle
        get() = remember(JewelTheme.instanceUuid) { retrieveConsoleTextStyle() }

    @get:Composable
    override val regular: TextStyle
        get() = labelTextStyle

    @get:Composable
    override val medium: TextStyle
        get() {
            val baseStyle = labelTextStyle
            return remember(baseStyle) { mutableStateOf(baseStyle.applyFrom(JBFont.medium())) }.value
        }

    @get:Composable
    override val small: TextStyle
        get() {
            val baseStyle = labelTextStyle
            return remember(baseStyle) { mutableStateOf(baseStyle.applyFrom(JBFont.small())) }.value
        }
}

// Do NOT use for editor and console fonts! They are already unscaled.
@OptIn(ExperimentalTextApi::class)
private fun TextStyle.applyFrom(font: Font) =
    copy(
        fontFamily = font.asComposeFontFamily(),
        fontSize = font.sizeSp, // Unscale font size to compensate for IDE zoom
        fontWeight = if (font.isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (font.isItalic) FontStyle.Italic else FontStyle.Normal,
        lineHeight = computeBaseLineHeightFor(font, treatAsUnscaled = false),
    )

private val Font.sizeSp
    get() = size.sp / UISettingsUtils.getInstance().currentIdeScale
