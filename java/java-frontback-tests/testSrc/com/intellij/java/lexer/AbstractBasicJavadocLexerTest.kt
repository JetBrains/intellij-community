// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.lexer

import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.testFramework.syntax.LexerTestCase

abstract class AbstractBasicJavadocLexerTest : LexerTestCase() {
  fun testSnippetAttributes() {
    doTest("""
      /**
      * {@snippet attr1 = "attr1 value" attr2 = 'attr2 value' attr3 = attr3Value : 
      *    foo {} 
      * }
      */
      """.trimIndent().trim(),
           """DOC_COMMENT_START ('/**')
DOC_SPACE ('\n')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' ')
DOC_INLINE_TAG_START ('{')
DOC_TAG_NAME ('@snippet')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('attr1')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('=')
DOC_SPACE (' ')
DOC_TAG_VALUE_QUOTE ('"')
DOC_TAG_VALUE_TOKEN ('attr1 value')
DOC_TAG_VALUE_QUOTE ('"')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('attr2')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('=')
DOC_SPACE (' ')
DOC_TAG_VALUE_QUOTE (''')
DOC_TAG_VALUE_TOKEN ('attr2 value')
DOC_TAG_VALUE_QUOTE (''')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('attr3')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('=')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('attr3Value')
DOC_SPACE (' ')
DOC_TAG_VALUE_COLON (':')
DOC_SPACE (' \n')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA ('    foo {}')
DOC_SPACE (' \n')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' ')
DOC_INLINE_TAG_END ('}')
DOC_SPACE ('\n')
DOC_COMMENT_END ('*/')""")
  }

  fun testAttributeNextToColon() {
    doTest("""
      /**
      * {@snippet attr1 = attrVal: 
      *    foo {} 
      * }
      */
      """.trimIndent().trim(),
           """DOC_COMMENT_START ('/**')
DOC_SPACE ('\n')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' ')
DOC_INLINE_TAG_START ('{')
DOC_TAG_NAME ('@snippet')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('attr1')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('=')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('attrVal')
DOC_TAG_VALUE_COLON (':')
DOC_SPACE (' \n')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA ('    foo {}')
DOC_SPACE (' \n')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' ')
DOC_INLINE_TAG_END ('}')
DOC_SPACE ('\n')
DOC_COMMENT_END ('*/')""")
  }

  fun testAttributeListOnSeveralLines() {
    doTest("""
      /**
       * Attributes:
       * {@snippet attr1=""
       * attr2=''
       *  :
       * }
       */
      """.trimIndent().trim(),
           """DOC_COMMENT_START ('/**')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' Attributes:')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' ')
DOC_INLINE_TAG_START ('{')
DOC_TAG_NAME ('@snippet')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('attr1')
DOC_TAG_VALUE_TOKEN ('=')
DOC_TAG_VALUE_QUOTE ('"')
DOC_TAG_VALUE_QUOTE ('"')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_SPACE (' ')
DOC_TAG_VALUE_TOKEN ('attr2')
DOC_TAG_VALUE_TOKEN ('=')
DOC_TAG_VALUE_QUOTE (''')
DOC_TAG_VALUE_QUOTE (''')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_SPACE ('  ')
DOC_TAG_VALUE_COLON (':')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' ')
DOC_INLINE_TAG_END ('}')
DOC_SPACE ('\n ')
DOC_COMMENT_END ('*/')""")
  }


  fun testColonOnNextLine() {
    doTest("""
      /**
       * {@snippet 
       * :
       * }
       */
      """.trimIndent().trim(),
           """DOC_COMMENT_START ('/**')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' ')
DOC_INLINE_TAG_START ('{')
DOC_TAG_NAME ('@snippet')
DOC_SPACE (' \n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_SPACE (' ')
DOC_TAG_VALUE_COLON (':')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' ')
DOC_INLINE_TAG_END ('}')
DOC_SPACE ('\n ')
DOC_COMMENT_END ('*/')""")
  }

  fun testBalancedBraces() {
    doTest("""
      /**
       * {@snippet 
       * :
       *     B0 {
       *     {
       *
       *     }
       *     B1 }
       * }
       */
      """.trimIndent().trim(),
           """DOC_COMMENT_START ('/**')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' ')
DOC_INLINE_TAG_START ('{')
DOC_TAG_NAME ('@snippet')
DOC_SPACE (' \n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_SPACE (' ')
DOC_TAG_VALUE_COLON (':')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA ('     B0 {')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA ('     {')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA ('     }')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA ('     B1 }')
DOC_SPACE ('\n ')
DOC_COMMENT_LEADING_ASTERISKS ('*')
DOC_COMMENT_DATA (' ')
DOC_INLINE_TAG_END ('}')
DOC_SPACE ('\n ')
DOC_COMMENT_END ('*/')""")
  }

  abstract override fun createLexer(): Lexer

  override val dirPath: String
    get() = ""
}