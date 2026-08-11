// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.code.highlighting

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.ExperimentalJewelApi

/**
 * Represents the semantic category of a syntax token used during code highlighting.
 *
 * Each value maps to a specific visual style in [SyntaxHighlightColors].
 */
@ApiStatus.Experimental
@ExperimentalJewelApi
public enum class TokenType {
    /** Language keywords: `val`, `var`, `fun`, `class`, `if`, `return`, `abstract`, `override`, etc. */
    KEYWORD,

    /** Built-in or primitive types: `String`, `Int`, `Boolean`, `void`, `bool`, etc. */
    TYPE,

    /** Language constants: `true`, `false`, `null`, `nil`, `None`, `undefined`, etc. */
    CONSTANT,

    /** Function or method names, e.g. the name in `fun myFunc(` or `myMethod(`. */
    FUNCTION_CALL,

    /** String literals, including multi-line and raw strings. */
    STRING,

    /** Line and block comments. */
    COMMENT,

    /** Numeric literals: integers, floats, hex, binary, etc. */
    NUMBER,

    /** Built-in functions or well-known standard library identifiers, e.g. `println`, `len`, `fmt.Println`. */
    BUILTIN,
}
