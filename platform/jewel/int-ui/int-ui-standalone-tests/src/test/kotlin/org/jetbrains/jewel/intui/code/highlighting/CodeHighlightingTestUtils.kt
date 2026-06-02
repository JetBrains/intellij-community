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

internal fun AnnotatedString.spansAt(index: Int): List<SpanStyle> =
    spanStyles.filter { it.start <= index && index < it.end }.map { it.item }

/** First non-[Color.Unspecified] color at [index], or null if the position is unstyled. */
internal fun AnnotatedString.colorAt(index: Int): Color? =
    spansAt(index).firstOrNull { it.color != Color.Unspecified }?.color
