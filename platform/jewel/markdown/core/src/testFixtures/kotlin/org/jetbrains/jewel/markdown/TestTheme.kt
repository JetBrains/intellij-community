// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Ordered.NumberFormatStyles.NumberFormatStyle
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Unordered.BulletCharStyles

public fun createThemeDefinition(): ThemeDefinition =
    ThemeDefinition(
        name = "Test",
        isDark = false,
        globalColors =
            GlobalColors(
                borders = BorderColors(normal = Color.Black, focused = Color.Black, disabled = Color.Black),
                outlines =
                    OutlineColors(
                        focused = Color.Black,
                        focusedWarning = Color.Black,
                        focusedError = Color.Black,
                        warning = Color.Black,
                        error = Color.Black,
                    ),
                text =
                    TextColors(
                        normal = Color.Black,
                        selected = Color.Black,
                        disabled = Color.Black,
                        disabledSelected = Color.Black,
                        info = Color.Black,
                        error = Color.Black,
                        warning = Color.Black,
                    ),
                panelBackground = Color.White,
                toolwindowBackground = Color.White,
            ),
        globalMetrics = GlobalMetrics(outlineWidth = 10.dp, rowHeight = 24.dp),
        defaultTextStyle = TextStyle.Default,
        editorTextStyle = TextStyle.Default,
        consoleTextStyle = TextStyle.Default,
        contentColor = Color.Black,
        colorPalette = ThemeColorPalette.Empty,
        iconData = ThemeIconData.Empty,
        disabledAppearanceValues = DisabledAppearanceValues(brightness = 33, contrast = -35, alpha = 100),
    )

public fun createMarkdownStyling(): MarkdownStyling {
    val mockSpanStyle = SpanStyle(Color.Black)
    val inlinesStyling =
        InlinesStyling(
            textStyle = TextStyle.Default,
            inlineCode = mockSpanStyle,
            link = mockSpanStyle,
            linkDisabled = mockSpanStyle,
            linkFocused = mockSpanStyle,
            linkHovered = mockSpanStyle,
            linkPressed = mockSpanStyle,
            linkVisited = mockSpanStyle,
            emphasis = mockSpanStyle,
            strongEmphasis = mockSpanStyle,
            inlineHtml = mockSpanStyle,
        )
    return MarkdownStyling(
        blockVerticalSpacing = 8.dp,
        paragraph = MarkdownStyling.Paragraph(inlinesStyling),
        heading =
            MarkdownStyling.Heading(
                h1 = MarkdownStyling.Heading.H1(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                h2 = MarkdownStyling.Heading.H2(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                h3 = MarkdownStyling.Heading.H3(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                h4 = MarkdownStyling.Heading.H4(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                h5 = MarkdownStyling.Heading.H5(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
                h6 = MarkdownStyling.Heading.H6(inlinesStyling, 1.dp, Color.Black, 2.dp, PaddingValues(4.dp)),
            ),
        blockQuote =
            MarkdownStyling.BlockQuote(
                padding = PaddingValues(4.dp),
                lineWidth = 2.dp,
                lineColor = Color.Gray,
                pathEffect = null,
                strokeCap = StrokeCap.Square,
                textColor = Color.Black,
            ),
        code =
            MarkdownStyling.Code(
                indented =
                    MarkdownStyling.Code.Indented(
                        editorTextStyle = TextStyle.Default.copy(fontSize = CODE_TEXT_SIZE.sp),
                        padding = PaddingValues(4.dp),
                        shape = RectangleShape,
                        background = Color.LightGray,
                        borderWidth = 0.dp,
                        borderColor = Color.DarkGray,
                        fillWidth = true,
                        scrollsHorizontally = true,
                    ),
                fenced =
                    MarkdownStyling.Code.Fenced(
                        editorTextStyle = TextStyle.Default.copy(fontSize = CODE_TEXT_SIZE.sp),
                        padding = PaddingValues(4.dp),
                        shape = RectangleShape,
                        background = Color.LightGray,
                        borderWidth = 0.dp,
                        borderColor = Color.DarkGray,
                        fillWidth = true,
                        scrollsHorizontally = true,
                        infoTextStyle = TextStyle.Default,
                        infoPadding = PaddingValues(2.dp),
                        infoPosition = MarkdownStyling.Code.Fenced.InfoPosition.TopStart,
                    ),
            ),
        list =
            MarkdownStyling.List(
                ordered =
                    MarkdownStyling.List.Ordered(
                        numberStyle = TextStyle.Default,
                        numberContentGap = 1.dp,
                        numberMinWidth = 2.dp,
                        numberTextAlign = TextAlign.Start,
                        itemVerticalSpacing = 4.dp,
                        itemVerticalSpacingTight = 2.dp,
                        padding = PaddingValues(4.dp),
                        numberFormatStyles =
                            MarkdownStyling.List.Ordered.NumberFormatStyles(firstLevel = NumberFormatStyle.Decimal),
                    ),
                unordered =
                    MarkdownStyling.List.Unordered(
                        bullet = '.',
                        bulletStyle = TextStyle.Default,
                        bulletContentGap = 1.dp,
                        itemVerticalSpacing = 4.dp,
                        itemVerticalSpacingTight = 2.dp,
                        padding = PaddingValues(4.dp),
                        markerMinWidth = 16.dp,
                        bulletCharStyles = BulletCharStyles(),
                    ),
            ),
        image =
            MarkdownStyling.Image(
                alignment = Alignment.Center,
                contentScale = ContentScale.Crop,
                padding = PaddingValues(8.dp),
                shape = RectangleShape,
                background = Color.Transparent,
                borderWidth = 1.dp,
                borderColor = Color.LightGray,
            ),
        thematicBreak =
            MarkdownStyling.ThematicBreak(padding = PaddingValues(4.dp), lineWidth = 2.dp, lineColor = Color.DarkGray),
        htmlBlock =
            MarkdownStyling.HtmlBlock(
                textStyle = TextStyle.Default,
                padding = PaddingValues(4.dp),
                shape = RectangleShape,
                background = Color.White,
                borderWidth = 1.dp,
                borderColor = Color.Gray,
                fillWidth = true,
            ),
    )
}

public const val CODE_TEXT_SIZE: Int = 10
