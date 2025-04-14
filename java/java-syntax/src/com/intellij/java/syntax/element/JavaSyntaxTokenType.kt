// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.syntaxElementTypeSetOf

/**
 * @see com.intellij.psi.JavaTokenType
 */
object JavaSyntaxTokenType {
  @JvmField val IDENTIFIER: SyntaxElementType = SyntaxElementType("IDENTIFIER")
  @JvmField val C_STYLE_COMMENT: SyntaxElementType = SyntaxElementType("C_STYLE_COMMENT")
  @JvmField val END_OF_LINE_COMMENT: SyntaxElementType = SyntaxElementType("END_OF_LINE_COMMENT")

  @JvmField val INTEGER_LITERAL: SyntaxElementType = SyntaxElementType("INTEGER_LITERAL")
  @JvmField val LONG_LITERAL: SyntaxElementType = SyntaxElementType("LONG_LITERAL")
  @JvmField val FLOAT_LITERAL: SyntaxElementType = SyntaxElementType("FLOAT_LITERAL")
  @JvmField val DOUBLE_LITERAL: SyntaxElementType = SyntaxElementType("DOUBLE_LITERAL")
  @JvmField val CHARACTER_LITERAL: SyntaxElementType = SyntaxElementType("CHARACTER_LITERAL")
  @JvmField val STRING_LITERAL: SyntaxElementType = SyntaxElementType("STRING_LITERAL")
  @JvmField val TEXT_BLOCK_LITERAL: SyntaxElementType = SyntaxElementType("TEXT_BLOCK_LITERAL")

  @JvmField val STRING_TEMPLATE_BEGIN: SyntaxElementType = SyntaxElementType("STRING_TEMPLATE_BEGIN")
  @JvmField val STRING_TEMPLATE_MID: SyntaxElementType = SyntaxElementType("STRING_TEMPLATE_MID")
  @JvmField val STRING_TEMPLATE_END: SyntaxElementType = SyntaxElementType("STRING_TEMPLATE_END")
  @JvmField val TEXT_BLOCK_TEMPLATE_BEGIN: SyntaxElementType = SyntaxElementType("TEXT_BLOCK_TEMPLATE_BEGIN")
  @JvmField val TEXT_BLOCK_TEMPLATE_MID: SyntaxElementType = SyntaxElementType("TEXT_BLOCK_TEMPLATE_MID")
  @JvmField val TEXT_BLOCK_TEMPLATE_END: SyntaxElementType = SyntaxElementType("TEXT_BLOCK_TEMPLATE_END")

  @JvmField val TRUE_KEYWORD: SyntaxElementType = SyntaxElementType("TRUE_KEYWORD")
  @JvmField val FALSE_KEYWORD: SyntaxElementType = SyntaxElementType("FALSE_KEYWORD")
  @JvmField val NULL_KEYWORD: SyntaxElementType = SyntaxElementType("NULL_KEYWORD")

  @JvmField val ABSTRACT_KEYWORD: SyntaxElementType = SyntaxElementType("ABSTRACT_KEYWORD")
  @JvmField val ASSERT_KEYWORD: SyntaxElementType = SyntaxElementType("ASSERT_KEYWORD")
  @JvmField val BOOLEAN_KEYWORD: SyntaxElementType = SyntaxElementType("BOOLEAN_KEYWORD")
  @JvmField val BREAK_KEYWORD: SyntaxElementType = SyntaxElementType("BREAK_KEYWORD")
  @JvmField val BYTE_KEYWORD: SyntaxElementType = SyntaxElementType("BYTE_KEYWORD")
  @JvmField val CASE_KEYWORD: SyntaxElementType = SyntaxElementType("CASE_KEYWORD")
  @JvmField val CATCH_KEYWORD: SyntaxElementType = SyntaxElementType("CATCH_KEYWORD")
  @JvmField val CHAR_KEYWORD: SyntaxElementType = SyntaxElementType("CHAR_KEYWORD")
  @JvmField val CLASS_KEYWORD: SyntaxElementType = SyntaxElementType("CLASS_KEYWORD")
  @JvmField val CONST_KEYWORD: SyntaxElementType = SyntaxElementType("CONST_KEYWORD")
  @JvmField val CONTINUE_KEYWORD: SyntaxElementType = SyntaxElementType("CONTINUE_KEYWORD")
  @JvmField val DEFAULT_KEYWORD: SyntaxElementType = SyntaxElementType("DEFAULT_KEYWORD")
  @JvmField val DO_KEYWORD: SyntaxElementType = SyntaxElementType("DO_KEYWORD")
  @JvmField val DOUBLE_KEYWORD: SyntaxElementType = SyntaxElementType("DOUBLE_KEYWORD")
  @JvmField val ELSE_KEYWORD: SyntaxElementType = SyntaxElementType("ELSE_KEYWORD")
  @JvmField val ENUM_KEYWORD: SyntaxElementType = SyntaxElementType("ENUM_KEYWORD")
  @JvmField val EXTENDS_KEYWORD: SyntaxElementType = SyntaxElementType("EXTENDS_KEYWORD")
  @JvmField val FINAL_KEYWORD: SyntaxElementType = SyntaxElementType("FINAL_KEYWORD")
  @JvmField val FINALLY_KEYWORD: SyntaxElementType = SyntaxElementType("FINALLY_KEYWORD")
  @JvmField val FLOAT_KEYWORD: SyntaxElementType = SyntaxElementType("FLOAT_KEYWORD")
  @JvmField val FOR_KEYWORD: SyntaxElementType = SyntaxElementType("FOR_KEYWORD")
  @JvmField val GOTO_KEYWORD: SyntaxElementType = SyntaxElementType("GOTO_KEYWORD")
  @JvmField val IF_KEYWORD: SyntaxElementType = SyntaxElementType("IF_KEYWORD")
  @JvmField val IMPLEMENTS_KEYWORD: SyntaxElementType = SyntaxElementType("IMPLEMENTS_KEYWORD")
  @JvmField val IMPORT_KEYWORD: SyntaxElementType = SyntaxElementType("IMPORT_KEYWORD")
  @JvmField val INSTANCEOF_KEYWORD: SyntaxElementType = SyntaxElementType("INSTANCEOF_KEYWORD")
  @JvmField val INT_KEYWORD: SyntaxElementType = SyntaxElementType("INT_KEYWORD")
  @JvmField val INTERFACE_KEYWORD: SyntaxElementType = SyntaxElementType("INTERFACE_KEYWORD")
  @JvmField val LONG_KEYWORD: SyntaxElementType = SyntaxElementType("LONG_KEYWORD")
  @JvmField val NATIVE_KEYWORD: SyntaxElementType = SyntaxElementType("NATIVE_KEYWORD")
  @JvmField val NEW_KEYWORD: SyntaxElementType = SyntaxElementType("NEW_KEYWORD")
  @JvmField val PACKAGE_KEYWORD: SyntaxElementType = SyntaxElementType("PACKAGE_KEYWORD")
  @JvmField val PRIVATE_KEYWORD: SyntaxElementType = SyntaxElementType("PRIVATE_KEYWORD")
  @JvmField val PUBLIC_KEYWORD: SyntaxElementType = SyntaxElementType("PUBLIC_KEYWORD")
  @JvmField val SHORT_KEYWORD: SyntaxElementType = SyntaxElementType("SHORT_KEYWORD")
  @JvmField val SUPER_KEYWORD: SyntaxElementType = SyntaxElementType("SUPER_KEYWORD")
  @JvmField val SWITCH_KEYWORD: SyntaxElementType = SyntaxElementType("SWITCH_KEYWORD")
  @JvmField val SYNCHRONIZED_KEYWORD: SyntaxElementType = SyntaxElementType("SYNCHRONIZED_KEYWORD")
  @JvmField val THIS_KEYWORD: SyntaxElementType = SyntaxElementType("THIS_KEYWORD")
  @JvmField val THROW_KEYWORD: SyntaxElementType = SyntaxElementType("THROW_KEYWORD")
  @JvmField val PROTECTED_KEYWORD: SyntaxElementType = SyntaxElementType("PROTECTED_KEYWORD")
  @JvmField val TRANSIENT_KEYWORD: SyntaxElementType = SyntaxElementType("TRANSIENT_KEYWORD")
  @JvmField val RETURN_KEYWORD: SyntaxElementType = SyntaxElementType("RETURN_KEYWORD")
  @JvmField val VOID_KEYWORD: SyntaxElementType = SyntaxElementType("VOID_KEYWORD")
  @JvmField val STATIC_KEYWORD: SyntaxElementType = SyntaxElementType("STATIC_KEYWORD")
  @JvmField val STRICTFP_KEYWORD: SyntaxElementType = SyntaxElementType("STRICTFP_KEYWORD")
  @JvmField val WHILE_KEYWORD: SyntaxElementType = SyntaxElementType("WHILE_KEYWORD")
  @JvmField val TRY_KEYWORD: SyntaxElementType = SyntaxElementType("TRY_KEYWORD")
  @JvmField val VOLATILE_KEYWORD: SyntaxElementType = SyntaxElementType("VOLATILE_KEYWORD")
  @JvmField val THROWS_KEYWORD: SyntaxElementType = SyntaxElementType("THROWS_KEYWORD")

  @JvmField val LPARENTH: SyntaxElementType = SyntaxElementType("LPARENTH")
  @JvmField val RPARENTH: SyntaxElementType = SyntaxElementType("RPARENTH")
  @JvmField val LBRACE: SyntaxElementType = SyntaxElementType("LBRACE")
  @JvmField val RBRACE: SyntaxElementType = SyntaxElementType("RBRACE")
  @JvmField val LBRACKET: SyntaxElementType = SyntaxElementType("LBRACKET")
  @JvmField val RBRACKET: SyntaxElementType = SyntaxElementType("RBRACKET")
  @JvmField val SEMICOLON: SyntaxElementType = SyntaxElementType("SEMICOLON")
  @JvmField val COMMA: SyntaxElementType = SyntaxElementType("COMMA")
  @JvmField val DOT: SyntaxElementType = SyntaxElementType("DOT")
  @JvmField val ELLIPSIS: SyntaxElementType = SyntaxElementType("ELLIPSIS")
  @JvmField val AT: SyntaxElementType = SyntaxElementType("AT")

  @JvmField val EQ: SyntaxElementType = SyntaxElementType("EQ")
  @JvmField val GT: SyntaxElementType = SyntaxElementType("GT")
  @JvmField val LT: SyntaxElementType = SyntaxElementType("LT")
  @JvmField val EXCL: SyntaxElementType = SyntaxElementType("EXCL")
  @JvmField val TILDE: SyntaxElementType = SyntaxElementType("TILDE")
  @JvmField val QUEST: SyntaxElementType = SyntaxElementType("QUEST")
  @JvmField val COLON: SyntaxElementType = SyntaxElementType("COLON")
  @JvmField val PLUS: SyntaxElementType = SyntaxElementType("PLUS")
  @JvmField val MINUS: SyntaxElementType = SyntaxElementType("MINUS")
  @JvmField val ASTERISK: SyntaxElementType = SyntaxElementType("ASTERISK")
  @JvmField val DIV: SyntaxElementType = SyntaxElementType("DIV")
  @JvmField val AND: SyntaxElementType = SyntaxElementType("AND")
  @JvmField val OR: SyntaxElementType = SyntaxElementType("OR")
  @JvmField val XOR: SyntaxElementType = SyntaxElementType("XOR")
  @JvmField val PERC: SyntaxElementType = SyntaxElementType("PERC")

  @JvmField val EQEQ: SyntaxElementType = SyntaxElementType("EQEQ")
  @JvmField val LE: SyntaxElementType = SyntaxElementType("LE")
  @JvmField val GE: SyntaxElementType = SyntaxElementType("GE")
  @JvmField val NE: SyntaxElementType = SyntaxElementType("NE")
  @JvmField val ANDAND: SyntaxElementType = SyntaxElementType("ANDAND")
  @JvmField val OROR: SyntaxElementType = SyntaxElementType("OROR")
  @JvmField val PLUSPLUS: SyntaxElementType = SyntaxElementType("PLUSPLUS")
  @JvmField val MINUSMINUS: SyntaxElementType = SyntaxElementType("MINUSMINUS")
  @JvmField val LTLT: SyntaxElementType = SyntaxElementType("LTLT")
  @JvmField val GTGT: SyntaxElementType = SyntaxElementType("GTGT")
  @JvmField val GTGTGT: SyntaxElementType = SyntaxElementType("GTGTGT")
  @JvmField val PLUSEQ: SyntaxElementType = SyntaxElementType("PLUSEQ")
  @JvmField val MINUSEQ: SyntaxElementType = SyntaxElementType("MINUSEQ")
  @JvmField val ASTERISKEQ: SyntaxElementType = SyntaxElementType("ASTERISKEQ")
  @JvmField val DIVEQ: SyntaxElementType = SyntaxElementType("DIVEQ")
  @JvmField val ANDEQ: SyntaxElementType = SyntaxElementType("ANDEQ")
  @JvmField val OREQ: SyntaxElementType = SyntaxElementType("OREQ")
  @JvmField val XOREQ: SyntaxElementType = SyntaxElementType("XOREQ")
  @JvmField val PERCEQ: SyntaxElementType = SyntaxElementType("PERCEQ")
  @JvmField val LTLTEQ: SyntaxElementType = SyntaxElementType("LTLTEQ")
  @JvmField val GTGTEQ: SyntaxElementType = SyntaxElementType("GTGTEQ")
  @JvmField val GTGTGTEQ: SyntaxElementType = SyntaxElementType("GTGTGTEQ")

  @JvmField val DOUBLE_COLON: SyntaxElementType = SyntaxElementType("DOUBLE_COLON")
  @JvmField val ARROW: SyntaxElementType = SyntaxElementType("ARROW")

  @JvmField val OPEN_KEYWORD: SyntaxElementType = SyntaxElementType("OPEN")
  @JvmField val MODULE_KEYWORD: SyntaxElementType = SyntaxElementType("MODULE")
  @JvmField val REQUIRES_KEYWORD: SyntaxElementType = SyntaxElementType("REQUIRES")
  @JvmField val EXPORTS_KEYWORD: SyntaxElementType = SyntaxElementType("EXPORTS")
  @JvmField val OPENS_KEYWORD: SyntaxElementType = SyntaxElementType("OPENS")
  @JvmField val USES_KEYWORD: SyntaxElementType = SyntaxElementType("USES")
  @JvmField val PROVIDES_KEYWORD: SyntaxElementType = SyntaxElementType("PROVIDES")
  @JvmField val TRANSITIVE_KEYWORD: SyntaxElementType = SyntaxElementType("TRANSITIVE")
  @JvmField val TO_KEYWORD: SyntaxElementType = SyntaxElementType("TO")
  @JvmField val WITH_KEYWORD: SyntaxElementType = SyntaxElementType("WITH")

  @JvmField val VAR_KEYWORD: SyntaxElementType = SyntaxElementType("VAR")
  @JvmField val YIELD_KEYWORD: SyntaxElementType = SyntaxElementType("YIELD")
  @JvmField val RECORD_KEYWORD: SyntaxElementType = SyntaxElementType("RECORD")

  @JvmField val VALUE_KEYWORD: SyntaxElementType = SyntaxElementType("VALUE_KEYWORD")
  @JvmField val SEALED_KEYWORD: SyntaxElementType = SyntaxElementType("SEALED")
  @JvmField val NON_SEALED_KEYWORD: SyntaxElementType = SyntaxElementType("NON_SEALED")
  @JvmField val PERMITS_KEYWORD: SyntaxElementType = SyntaxElementType("PERMITS")
  @JvmField val WHEN_KEYWORD: SyntaxElementType = SyntaxElementType("WHEN_KEYWORD")

  @JvmField val JAVA_TOKEN_TYPE_SET: SyntaxElementTypeSet = syntaxElementTypeSetOf(
    IDENTIFIER,
    C_STYLE_COMMENT,
    END_OF_LINE_COMMENT,

    INTEGER_LITERAL,
    LONG_LITERAL,
    FLOAT_LITERAL,
    DOUBLE_LITERAL,
    CHARACTER_LITERAL,
    STRING_LITERAL,
    TEXT_BLOCK_LITERAL,

    STRING_TEMPLATE_BEGIN,
    STRING_TEMPLATE_MID,
    STRING_TEMPLATE_END,
    TEXT_BLOCK_TEMPLATE_BEGIN,
    TEXT_BLOCK_TEMPLATE_MID,
    TEXT_BLOCK_TEMPLATE_END,

    TRUE_KEYWORD,
    FALSE_KEYWORD,
    NULL_KEYWORD,

    ABSTRACT_KEYWORD,
    ASSERT_KEYWORD,
    BOOLEAN_KEYWORD,
    BREAK_KEYWORD,
    BYTE_KEYWORD,
    CASE_KEYWORD,
    CATCH_KEYWORD,
    CHAR_KEYWORD,
    CLASS_KEYWORD,
    CONST_KEYWORD,
    CONTINUE_KEYWORD,
    DEFAULT_KEYWORD,
    DO_KEYWORD,
    DOUBLE_KEYWORD,
    ELSE_KEYWORD,
    ENUM_KEYWORD,
    EXTENDS_KEYWORD,
    FINAL_KEYWORD,
    FINALLY_KEYWORD,
    FLOAT_KEYWORD,
    FOR_KEYWORD,
    GOTO_KEYWORD,
    IF_KEYWORD,
    IMPLEMENTS_KEYWORD,
    IMPORT_KEYWORD,
    INSTANCEOF_KEYWORD,
    INT_KEYWORD,
    INTERFACE_KEYWORD,
    LONG_KEYWORD,
    NATIVE_KEYWORD,
    NEW_KEYWORD,
    PACKAGE_KEYWORD,
    PRIVATE_KEYWORD,
    PUBLIC_KEYWORD,
    SHORT_KEYWORD,
    SUPER_KEYWORD,
    SWITCH_KEYWORD,
    SYNCHRONIZED_KEYWORD,
    THIS_KEYWORD,
    THROW_KEYWORD,
    PROTECTED_KEYWORD,
    TRANSIENT_KEYWORD,
    RETURN_KEYWORD,
    VOID_KEYWORD,
    STATIC_KEYWORD,
    STRICTFP_KEYWORD,
    WHILE_KEYWORD,
    TRY_KEYWORD,
    VOLATILE_KEYWORD,
    THROWS_KEYWORD,

    LPARENTH,
    RPARENTH,
    LBRACE,
    RBRACE,
    LBRACKET,
    RBRACKET,
    SEMICOLON,
    COMMA,
    DOT,
    ELLIPSIS,
    AT,

    EQ,
    GT,
    LT,
    EXCL,
    TILDE,
    QUEST,
    COLON,
    PLUS,
    MINUS,
    ASTERISK,
    DIV,
    AND,
    OR,
    XOR,
    PERC,

    EQEQ,
    LE,
    GE,
    NE,
    ANDAND,
    OROR,
    PLUSPLUS,
    MINUSMINUS,
    LTLT,
    GTGT,
    GTGTGT,
    PLUSEQ,
    MINUSEQ,
    ASTERISKEQ,
    DIVEQ,
    ANDEQ,
    OREQ,
    XOREQ,
    PERCEQ,
    LTLTEQ,
    GTGTEQ,
    GTGTGTEQ,

    DOUBLE_COLON,
    ARROW,

    OPEN_KEYWORD,
    MODULE_KEYWORD,
    REQUIRES_KEYWORD,
    EXPORTS_KEYWORD,
    OPENS_KEYWORD,
    USES_KEYWORD,
    PROVIDES_KEYWORD,
    TRANSITIVE_KEYWORD,
    TO_KEYWORD,
    WITH_KEYWORD,

    VAR_KEYWORD,
    YIELD_KEYWORD,
    RECORD_KEYWORD,

    VALUE_KEYWORD,
    SEALED_KEYWORD,
    NON_SEALED_KEYWORD,
    PERMITS_KEYWORD,
    WHEN_KEYWORD,
  )
}