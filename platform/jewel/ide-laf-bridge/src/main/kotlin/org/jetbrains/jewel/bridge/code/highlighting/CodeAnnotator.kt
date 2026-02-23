// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.code.highlighting

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import java.awt.Font
import org.jetbrains.jewel.bridge.toComposeColorOrUnspecified

internal class CodeAnnotator {
    fun annotate(code: String, highlighter: SyntaxHighlighter, colorScheme: EditorColorsScheme): AnnotatedString =
        buildAnnotatedString {
            with(highlighter.highlightingLexer) {
                start(code)

                while (tokenType != null) {
                    val attributes: TextAttributes? = run {
                        val attrKey = highlighter.getTokenHighlights(tokenType).lastOrNull() ?: return@run null
                        colorScheme.getAttributes(attrKey) ?: attrKey.defaultAttributes
                    }
                    withTextAttributes(attributes) { append(tokenText) }
                    advance()
                }
            }
        }

    private fun AnnotatedString.Builder.withTextAttributes(
        textAttributes: TextAttributes?,
        block: AnnotatedString.Builder.() -> Unit,
    ) {
        if (textAttributes == null) {
            return block()
        }
        withStyle(textAttributes.toSpanStyle(), block)
    }

    private fun TextAttributes.toSpanStyle() =
        SpanStyle(
            color = foregroundColor.toComposeColorOrUnspecified(),
            fontWeight = if (fontType and Font.BOLD != 0) FontWeight.Bold else null,
            fontStyle = if (fontType and Font.ITALIC != 0) FontStyle.Italic else null,
            background = backgroundColor.toComposeColorOrUnspecified(),
            textDecoration =
                when (effectType) {
                    EffectType.LINE_UNDERSCORE -> TextDecoration.Underline
                    EffectType.STRIKEOUT -> TextDecoration.LineThrough
                    else -> null
                },
        )
}
