// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.jetbrains.jewel.intui.standalone.code.highlighting.SyntaxHighlightColors

/**
 * A test-only [SyntaxHighlightColors] palette where each
 * [org.jetbrains.jewel.intui.standalone.code.highlighting.TokenType] maps to a unique, easily identifiable color. Use
 * this in grammar tests so assertions are purely about tokenizer correctness, not production palette values.
 */
internal val testColors =
    SyntaxHighlightColors(
        keyword = Color.Red,
        type = Color.Blue,
        constant = Color.Green,
        functionCall = Color.Yellow,
        string = Color.Cyan,
        comment = Color.Gray,
        number = Color.Magenta,
        builtin = Color.White,
    )

/** All [SpanStyle]s covering character offset [index], in [spanStyles] order. */
internal fun AnnotatedString.spansAt(index: Int): List<SpanStyle> =
    spanStyles.filter { it.start <= index && index < it.end }.map { it.item }

/** First non-[Color.Unspecified] color at character offset [index], or null if the position is unstyled. */
internal fun AnnotatedString.colorAt(index: Int): Color? =
    spansAt(index).firstOrNull { it.color != Color.Unspecified }?.color

/**
 * Color of the [index]-th [SpanStyle] in [spanStyles] order (i.e. the n-th highlighted token), or null if that span has
 * no explicit color. Unlike [colorAt], [index] is a span index, not a character offset.
 */
internal fun AnnotatedString.spanColorAt(index: Int): Color? =
    spanStyles[index].item.color.takeIf { it != Color.Unspecified }
