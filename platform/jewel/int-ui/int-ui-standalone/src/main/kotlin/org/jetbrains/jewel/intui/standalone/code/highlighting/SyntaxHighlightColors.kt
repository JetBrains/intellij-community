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
 * The default light and dark palettes are based on IntelliJ IDEA's "Light" and "Dark" editor color schemes, the two the
 * New UI selects by default. Colors are intentionally not sourced from the Jewel UI theme palette — those are UI chrome
 * colors, not editor token colors.
 *
 * Use [SyntaxHighlightColors.light] and [SyntaxHighlightColors.dark] to get the built-in palettes, or construct your
 * own instance to fully customize token colors.
 *
 * @param keyword Color for language keywords (e.g., `val`, `fun`, `class`, `if`). Rendered bold.
 * @param type Color for built-in or primitive types (e.g., `String`, `Int`, `void`, `bool`).
 * @param constant Color for language constants (e.g., `true`, `false`, `null`, `nil`). Rendered italic.
 * @param functionCall Color for function and method names.
 * @param string Color for string literals.
 * @param comment Color for line and block comments. Rendered italic.
 * @param number Color for numeric literals.
 * @param builtin Color for well-known built-in functions and standard library identifiers. Rendered italic.
 * @param propertyKey Color for keys in data languages, e.g. the `"name"` in JSON's `"name": 1`.
 * @param operator Color for operators, e.g. `+`, `==`, `&&`, `|`.
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
    public val propertyKey: Color,
    public val operator: Color,
) {
    internal fun styleFor(tokenType: TokenType): SpanStyle =
        when (tokenType) {
            TokenType.KEYWORD -> SpanStyle(color = keyword, fontWeight = FontWeight.Bold)
            TokenType.TYPE -> SpanStyle(color = type)
            TokenType.CONSTANT -> SpanStyle(color = constant, fontStyle = FontStyle.Italic)
            TokenType.FUNCTION_CALL -> SpanStyle(color = functionCall)
            TokenType.STRING -> SpanStyle(color = string)
            TokenType.COMMENT -> SpanStyle(color = comment, fontStyle = FontStyle.Italic)
            TokenType.NUMBER -> SpanStyle(color = number)
            TokenType.BUILTIN -> SpanStyle(color = builtin, fontStyle = FontStyle.Italic)
            TokenType.PROPERTY_KEY -> SpanStyle(color = propertyKey)
            TokenType.OPERATOR -> SpanStyle(color = operator)
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
        if (propertyKey != other.propertyKey) return false
        if (operator != other.operator) return false

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
        result = 31 * result + propertyKey.hashCode()
        result = 31 * result + operator.hashCode()
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
            "builtin=$builtin, " +
            "propertyKey=$propertyKey, " +
            "operator=$operator" +
            ")"

    /** Companion object for [SyntaxHighlightColors], holding the built-in palettes. */
    public companion object {
        /**
         * Returns a [SyntaxHighlightColors] palette matching IntelliJ IDEA's "Light" editor color scheme, the one the
         * New UI selects by default.
         *
         * Colors sourced from `platform/platform-resources/src/themes/expUI/expUI_lightScheme.xml`, scheme `Light`,
         * following its `parent_scheme="Default"` and then each key's own fallback. Token types the scheme gives no
         * foreground use [Color.Unspecified], which inherits the ambient text color.
         */
        public fun light(): SyntaxHighlightColors =
            SyntaxHighlightColors(
                keyword = Color(0xFF0033B3), // DEFAULT_KEYWORD: value="33b3"
                type = Color.Unspecified, // DEFAULT_CLASS_REFERENCE: unset, resolves to DEFAULT_IDENTIFIER
                constant = Color(0xFF871094), // DEFAULT_CONSTANT: value="871094"
                functionCall = Color(0xFF00627A), // DEFAULT_FUNCTION_DECLARATION: value="627a"
                string = Color(0xFF067D17), // DEFAULT_STRING: value="67d17"
                comment = Color(0xFF8C8C8C), // DEFAULT_LINE_COMMENT: value="8c8c8c"
                number = Color(0xFF1750EB), // DEFAULT_NUMBER: value="1750eb"
                builtin = Color.Unspecified, // DEFAULT_PREDEFINED_SYMBOL: italic only, no foreground
                propertyKey = Color(0xFF871094), // DEFAULT_INSTANCE_FIELD: value="871094"
                operator = Color.Unspecified, // DEFAULT_OPERATION_SIGN: unset, and the key has no fallback
            )

        /**
         * Returns a [SyntaxHighlightColors] palette matching IntelliJ IDEA's "Dark" editor color scheme, the one the
         * New UI selects by default. This is not Darcula, which is the older scheme it inherits from.
         *
         * Colors sourced from `platform/platform-resources/src/themes/expUI/expUI_darkScheme.xml`, scheme `Dark`.
         */
        public fun dark(): SyntaxHighlightColors =
            SyntaxHighlightColors(
                keyword = Color(0xFFCF8E6D), // DEFAULT_KEYWORD: value="cf8e6d"
                // DEFAULT_CLASS_REFERENCE is "bcbec4", which is this scheme's own TEXT foreground — deliberately
                // plain. Unspecified says the same thing without pinning us to a dark surface.
                type = Color.Unspecified,
                constant = Color(0xFFC77DBB), // DEFAULT_CONSTANT: value="c77dbb"
                functionCall = Color(0xFF56A8F5), // DEFAULT_FUNCTION_DECLARATION: value="56a8f5"
                string = Color(0xFF6AAB73), // DEFAULT_STRING: value="6aab73"
                comment = Color(0xFF7A7E85), // DEFAULT_LINE_COMMENT: value="7a7e85"
                number = Color(0xFF2AACB8), // DEFAULT_NUMBER: value="2aacb8"
                builtin = Color.Unspecified, // DEFAULT_PREDEFINED_SYMBOL: italic only, no foreground
                propertyKey = Color(0xFFC77DBB), // DEFAULT_INSTANCE_FIELD: value="c77dbb"
                operator = Color.Unspecified, // DEFAULT_OPERATION_SIGN: "bcbec4", the TEXT foreground again
            )
    }
}
