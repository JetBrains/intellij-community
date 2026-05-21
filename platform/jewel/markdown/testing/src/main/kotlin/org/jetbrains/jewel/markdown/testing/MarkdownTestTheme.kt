// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.markdown.testing

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.foundation.code.highlighting.LocalCodeHighlighter
import org.jetbrains.jewel.foundation.code.highlighting.NoOpCodeHighlighter
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Ordered.NumberFormatStyles.NumberFormatStyle
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling.List.Unordered.BulletCharStyles
import org.jetbrains.jewel.ui.component.styling.DividerMetrics
import org.jetbrains.jewel.ui.component.styling.DividerStyle
import org.jetbrains.jewel.ui.component.styling.LocalDividerStyle
import org.jetbrains.jewel.ui.component.styling.LocalScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarColors
import org.jetbrains.jewel.ui.component.styling.ScrollbarMetrics
import org.jetbrains.jewel.ui.component.styling.ScrollbarStyle
import org.jetbrains.jewel.ui.component.styling.ScrollbarVisibility
import org.jetbrains.jewel.ui.component.styling.TrackClickBehavior

@Composable
fun MarkdownTestTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalCodeHighlighter provides NoOpCodeHighlighter,
        LocalDividerStyle provides createMarkdownTestDividerStyle(),
        LocalScrollbarStyle provides createMarkdownTestScrollbarStyle(),
        LocalDensity provides Density(1f),
    ) {
        JewelTheme(createMarkdownTestThemeDefinition()) { content() }
    }
}

fun createMarkdownTestDividerStyle() =
    DividerStyle(color = Color.Black, metrics = DividerMetrics(thickness = 1.dp, startIndent = 0.dp))

fun createMarkdownTestScrollbarStyle() =
    ScrollbarStyle(
        colors =
            ScrollbarColors(
                thumbBackground = Color.Black,
                thumbBorderActive = Color.Black,
                thumbBackgroundActive = Color.Black,
                thumbOpaqueBackground = Color.Black,
                thumbOpaqueBackgroundHovered = Color.Black,
                thumbBorder = Color.Black,
                thumbOpaqueBorder = Color.Black,
                thumbOpaqueBorderHovered = Color.Black,
                trackBackground = Color.Black,
                trackBackgroundExpanded = Color.Black,
                trackOpaqueBackground = Color.Black,
                trackOpaqueBackgroundHovered = Color.Black,
            ),
        metrics = ScrollbarMetrics(thumbCornerSize = CornerSize(1.dp), minThumbLength = 1.dp),
        trackClickBehavior = TrackClickBehavior.NextPage,
        scrollbarVisibility =
            ScrollbarVisibility.AlwaysVisible(
                trackThickness = 1.dp,
                trackPadding = PaddingValues(1.dp),
                trackPaddingWithBorder = PaddingValues(1.dp),
                thumbColorAnimationDuration = 500.milliseconds,
                trackColorAnimationDuration = 500.milliseconds,
                scrollbarBackgroundColorLight = Color.White,
                scrollbarBackgroundColorDark = Color.White,
            ),
    )

fun createMarkdownTestThemeDefinition(): ThemeDefinition =
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

fun createMarkdownTestStyling(codeEditorTextStyle: TextStyle = TextStyle.Default): MarkdownStyling {
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
                        editorTextStyle = codeEditorTextStyle,
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
                        editorTextStyle = codeEditorTextStyle,
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
