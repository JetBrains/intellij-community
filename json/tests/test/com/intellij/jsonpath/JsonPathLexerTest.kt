// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

import com.intellij.jsonpath.lexer.JsonPathLexer
import com.intellij.lexer.Lexer
import com.intellij.testFramework.LexerTestCase

class JsonPathLexerTest : LexerTestCase() {
  private val ROOT: String = "\$"

  override fun createLexer(): Lexer = JsonPathLexer()
  override fun getDirPath(): String = "unused"

  fun testRoot() {
    doTest("\$", "ROOT_CONTEXT ('\$')")
    doTest("@", "EVAL_CONTEXT ('@')")
    doTest("name", "IDENTIFIER ('name')")

    doTest("\$[0]", """
      ROOT_CONTEXT ('$ROOT')
      LBRACKET ('[')
      INTEGER_NUMBER ('0')
      RBRACKET (']')
    """.trimIndent())

    doTest("@[-100]", """
      EVAL_CONTEXT ('@')
      LBRACKET ('[')
      INTEGER_NUMBER ('-100')
      RBRACKET (']')
    """.trimIndent())
  }

  fun testDottedPaths() {
    doTest("\$.path", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('path')
    """.trimIndent())

    doTest("\$..path", """
      ROOT_CONTEXT ('$ROOT')
      RECURSIVE_DESCENT ('..')
      IDENTIFIER ('path')
    """.trimIndent())

    doTest("@.path", """
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('path')
    """.trimIndent())

    doTest("@..path", """
      EVAL_CONTEXT ('@')
      RECURSIVE_DESCENT ('..')
      IDENTIFIER ('path')
    """.trimIndent())

    doTest("\$.path", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('path')
    """.trimIndent())

    doTest("\$.long.path.with.root", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('long')
      DOT ('.')
      IDENTIFIER ('path')
      DOT ('.')
      IDENTIFIER ('with')
      DOT ('.')
      IDENTIFIER ('root')
    """.trimIndent())

    doTest("@.long.path.with.eval", """
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('long')
      DOT ('.')
      IDENTIFIER ('path')
      DOT ('.')
      IDENTIFIER ('with')
      DOT ('.')
      IDENTIFIER ('eval')
    """.trimIndent())
  }

  fun testQuotedPaths() {
    doTest("\$['quoted']['path']", """
      ROOT_CONTEXT ('$ROOT')
      LBRACKET ('[')
      SINGLE_QUOTED_STRING (''quoted'')
      RBRACKET (']')
      LBRACKET ('[')
      SINGLE_QUOTED_STRING (''path'')
      RBRACKET (']')
    """.trimIndent())
    doTest("\$.['quoted'].path", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      LBRACKET ('[')
      SINGLE_QUOTED_STRING (''quoted'')
      RBRACKET (']')
      DOT ('.')
      IDENTIFIER ('path')
    """.trimIndent())
    doTest("\$.[\"quo\\ted\"]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      LBRACKET ('[')
      DOUBLE_QUOTED_STRING ('"quo\ted"')
      RBRACKET (']')
    """.trimIndent())
  }

  fun testFilterExpression() {
    doTest("\$.demo[?(@.filter > 2)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('filter')
      WHITE_SPACE (' ')
      GT_OP ('>')
      WHITE_SPACE (' ')
      INTEGER_NUMBER ('2')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())

    doTest("\$.demo[?(@.filter == 7.2)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('filter')
      WHITE_SPACE (' ')
      EQ_OP ('==')
      WHITE_SPACE (' ')
      DOUBLE_NUMBER ('7.2')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())

    doTest("\$.demo[?(@.filter != 'value')]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('filter')
      WHITE_SPACE (' ')
      NE_OP ('!=')
      WHITE_SPACE (' ')
      SINGLE_QUOTED_STRING (''value'')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())

    doTest("\$.demo[?(@.filter == true)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('filter')
      WHITE_SPACE (' ')
      EQ_OP ('==')
      WHITE_SPACE (' ')
      TRUE ('true')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())

    doTest("\$.demo[?(@.filter != false)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('filter')
      WHITE_SPACE (' ')
      NE_OP ('!=')
      WHITE_SPACE (' ')
      FALSE ('false')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())

    doTest("\$.demo[?(@.null != null)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('null')
      WHITE_SPACE (' ')
      NE_OP ('!=')
      WHITE_SPACE (' ')
      NULL ('null')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())

    doTest("\$.demo[?('a' in @.in)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      SINGLE_QUOTED_STRING (''a'')
      WHITE_SPACE (' ')
      IN_OP ('in')
      WHITE_SPACE (' ')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('in')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())
  }

  fun testBooleanOperations() {
    doTest("\$.demo[?(@.a>=10 && $.b<=2)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('a')
      GE_OP ('>=')
      INTEGER_NUMBER ('10')
      WHITE_SPACE (' ')
      AND_OP ('&&')
      WHITE_SPACE (' ')
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('b')
      LE_OP ('<=')
      INTEGER_NUMBER ('2')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())
  }

  fun testIndexExpression() {
    doTest("\$.demo[(@.length - 1)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('length')
      WHITE_SPACE (' ')
      MINUS_OP ('-')
      WHITE_SPACE (' ')
      INTEGER_NUMBER ('1')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())
  }

  fun testRegexLiteral() {
    doTest("\$.demo[?(@.attr =~ /[a-z]/)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('attr')
      WHITE_SPACE (' ')
      RE_OP ('=~')
      WHITE_SPACE (' ')
      REGEX_STRING ('/[a-z]/')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())

    doTest("\$.demo[?(@.attr =~ /[0-9]/i)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('attr')
      WHITE_SPACE (' ')
      RE_OP ('=~')
      WHITE_SPACE (' ')
      REGEX_STRING ('/[0-9]/i')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())

    doTest("\$.demo[?(@.attr =~ /[0-9]/iu)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('attr')
      WHITE_SPACE (' ')
      RE_OP ('=~')
      WHITE_SPACE (' ')
      REGEX_STRING ('/[0-9]/iu')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())

    doTest("\$.demo[?(@ =~ /test/U)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      WHITE_SPACE (' ')
      RE_OP ('=~')
      WHITE_SPACE (' ')
      REGEX_STRING ('/test/U')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())
  }

  fun testWildcardMultiplyOperators() {
    doTest("\$.demo[*].demo[?(@.attr * 2 == 10)]", """
      ROOT_CONTEXT ('$ROOT')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      WILDCARD ('*')
      RBRACKET (']')
      DOT ('.')
      IDENTIFIER ('demo')
      LBRACKET ('[')
      FILTER_OPERATOR ('?')
      LPARENTH ('(')
      EVAL_CONTEXT ('@')
      DOT ('.')
      IDENTIFIER ('attr')
      WHITE_SPACE (' ')
      MULTIPLY_OP ('*')
      WHITE_SPACE (' ')
      INTEGER_NUMBER ('2')
      WHITE_SPACE (' ')
      EQ_OP ('==')
      WHITE_SPACE (' ')
      INTEGER_NUMBER ('10')
      RPARENTH (')')
      RBRACKET (']')
    """.trimIndent())
  }
}