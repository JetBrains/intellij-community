// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.code.highlighting

import kotlin.test.assertEquals
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenRule
import org.jetbrains.jewel.intui.standalone.code.highlighting.TokenType
import org.junit.jupiter.api.Test

internal class TokenRuleTest {
    @Test
    fun `comment factory colors entire match`() {
        val rule = TokenRule.comment("//.*")
        assertEquals(mapOf(0 to TokenType.COMMENT), rule.captures)
    }

    @Test
    fun `string factory colors entire match`() {
        val rule = TokenRule.string("\".*\"")
        assertEquals(mapOf(0 to TokenType.STRING), rule.captures)
    }

    @Test
    fun `keyword factory colors entire match`() {
        val rule = TokenRule.keyword("\\b(if|else)\\b")
        assertEquals(mapOf(0 to TokenType.KEYWORD), rule.captures)
    }

    @Test
    fun `type factory colors entire match`() {
        val rule = TokenRule.type("\\bString\\b")
        assertEquals(mapOf(0 to TokenType.TYPE), rule.captures)
    }

    @Test
    fun `constant factory colors entire match`() {
        val rule = TokenRule.constant("\\b(true|false|null)\\b")
        assertEquals(mapOf(0 to TokenType.CONSTANT), rule.captures)
    }

    @Test
    fun `number factory colors entire match`() {
        val rule = TokenRule.number("\\b[0-9]+\\b")
        assertEquals(mapOf(0 to TokenType.NUMBER), rule.captures)
    }

    @Test
    fun `builtin factory colors entire match`() {
        val rule = TokenRule.builtin("\\bprintln\\b")
        assertEquals(mapOf(0 to TokenType.BUILTIN), rule.captures)
    }

    @Test
    fun `functionCall factory colors group 1`() {
        val rule = TokenRule.functionCall("\\b([A-Za-z_]\\w*)\\s*(?=\\()")
        assertEquals(mapOf(1 to TokenType.FUNCTION_CALL), rule.captures)
    }

    @Test
    fun `functionDeclaration factory colors group 1 as keyword and group 2 as function call`() {
        val rule = TokenRule.functionDeclaration("\\b(fun)\\s+([A-Za-z_]\\w*)")
        assertEquals(mapOf(1 to TokenType.KEYWORD, 2 to TokenType.FUNCTION_CALL), rule.captures)
    }

    @Test
    fun `typeDeclaration factory colors group 1 as keyword and group 2 as builtin`() {
        val rule = TokenRule.typeDeclaration("\\b(class)\\s+([A-Za-z_]\\w*)")
        assertEquals(mapOf(1 to TokenType.KEYWORD, 2 to TokenType.BUILTIN), rule.captures)
    }
}
