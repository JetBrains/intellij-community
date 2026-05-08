// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Defines the colors used to render each [TokenType] in a syntax-highlighted code block.
 *
 * The default light and dark palettes are based on IntelliJ IDEA's "Default" and "Darcula" editor color schemes
 * respectively. Colors are intentionally not sourced from the Jewel UI theme palette — those are UI chrome colors, not
 * editor token colors.
 *
 * Use [SyntaxHighlightColors.light] and [SyntaxHighlightColors.dark] to get the built-in palettes, or construct your
 * own instance to fully customize token colors.
 *
 * @param keyword Color for language keywords (e.g., `val`, `fun`, `class`, `if`). Rendered bold.
 * @param type Color for built-in or primitive types (e.g., `String`, `Int`, `void`, `bool`).
 * @param constant Color for language constants (e.g., `true`, `false`, `null`, `nil`).
 * @param functionCall Color for function and method names.
 * @param string Color for string literals.
 * @param comment Color for line and block comments. Rendered italic.
 * @param number Color for numeric literals.
 * @param builtin Color for well-known built-in functions and standard library identifiers.
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
@Immutable
public class SyntaxHighlightColors(
    public val keyword: Color,
    public val type: Color,
    public val constant: Color,
    public val functionCall: Color,
    public val string: Color,
    public val comment: Color,
    public val number: Color,
    public val builtin: Color,
) {
    internal fun styleFor(tokenType: TokenType): SpanStyle =
        when (tokenType) {
            TokenType.KEYWORD -> SpanStyle(color = keyword, fontWeight = FontWeight.Bold)
            TokenType.TYPE -> SpanStyle(color = type)
            TokenType.CONSTANT -> SpanStyle(color = constant)
            TokenType.FUNCTION_CALL -> SpanStyle(color = functionCall)
            TokenType.STRING -> SpanStyle(color = string)
            TokenType.COMMENT -> SpanStyle(color = comment, fontStyle = FontStyle.Italic)
            TokenType.NUMBER -> SpanStyle(color = number)
            TokenType.BUILTIN -> SpanStyle(color = builtin)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SyntaxHighlightColors

        if (keyword != other.keyword) return false
        if (type != other.type) return false
        if (constant != other.constant) return false
        if (functionCall != other.functionCall) return false
        if (string != other.string) return false
        if (comment != other.comment) return false
        if (number != other.number) return false
        if (builtin != other.builtin) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyword.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + constant.hashCode()
        result = 31 * result + functionCall.hashCode()
        result = 31 * result + string.hashCode()
        result = 31 * result + comment.hashCode()
        result = 31 * result + number.hashCode()
        result = 31 * result + builtin.hashCode()
        return result
    }

    override fun toString(): String =
        "SyntaxHighlightColors(" +
            "keyword=$keyword, " +
            "type=$type, " +
            "constant=$constant, " +
            "functionCall=$functionCall, " +
            "string=$string, " +
            "comment=$comment, " +
            "number=$number, " +
            "builtin=$builtin" +
            ")"

    public companion object {
        /**
         * Returns a [SyntaxHighlightColors] palette matching IntelliJ IDEA's "Default" (light) editor color scheme.
         *
         * Colors sourced from `platform/platform-resources/src/DefaultColorSchemesManager.xml`, scheme `Default`. Token
         * types not explicitly defined in that scheme (e.g., function names, type names) use [Color.Unspecified], which
         * means they inherit the ambient text color and appear unstyled.
         */
        public fun light(): SyntaxHighlightColors =
            SyntaxHighlightColors(
                keyword = Color(0xFF000080), // DEFAULT_KEYWORD: value="80" → 000080
                type = Color.Unspecified, // not defined in Default scheme — inherits
                constant = Color(0xFF660E7A), // DEFAULT_CONSTANT: value="660e7a"
                functionCall = Color.Unspecified, // DEFAULT_FUNCTION_DECLARATION: not defined in Default — inherits
                string = Color(0xFF008000), // DEFAULT_STRING: value="008000"
                comment = Color(0xFF808080), // DEFAULT_LINE_COMMENT / DEFAULT_BLOCK_COMMENT: value="808080"
                number = Color(0xFF0000FF), // DEFAULT_NUMBER: value="ff" → 0000ff
                builtin = Color.Unspecified, // DEFAULT_PREDEFINED_SYMBOL: bold only, no color — inherits
            )

        /**
         * Returns a [SyntaxHighlightColors] palette matching IntelliJ IDEA's "Darcula" (dark) editor color scheme.
         *
         * Colors sourced from `platform/platform-resources/src/DefaultColorSchemesManager.xml`, scheme `Darcula`.
         */
        public fun dark(): SyntaxHighlightColors =
            SyntaxHighlightColors(
                keyword = Color(0xFFCC7832), // DEFAULT_KEYWORD: value="cc7832"
                type = Color(0xFF769AA5), // DEFAULT_CLASS_REFERENCE: value="769aa5"
                constant = Color(0xFF9876AA), // DEFAULT_CONSTANT: value="9876aa"
                functionCall = Color(0xFFFFC66D), // DEFAULT_FUNCTION_DECLARATION: value="ffc66d"
                string = Color(0xFF6A8759), // DEFAULT_STRING: value="6a8759"
                comment = Color(0xFF808080), // DEFAULT_LINE_COMMENT / DEFAULT_BLOCK_COMMENT: value="808080"
                number = Color(0xFF6897BB), // DEFAULT_NUMBER: value="6897bb"
                builtin = Color(0xFF9876AA), // DEFAULT_CONSTANT (inherited by predefined symbols in Darcula)
            )
    }
}
