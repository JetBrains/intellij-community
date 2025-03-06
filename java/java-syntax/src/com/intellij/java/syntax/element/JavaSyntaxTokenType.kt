// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.syntaxElementTypeSetOf

/**
 * @see com.intellij.psi.JavaTokenType
 */
object JavaSyntaxTokenType {
  val IDENTIFIER: SyntaxElementType = SyntaxElementType("IDENTIFIER")
  val C_STYLE_COMMENT: SyntaxElementType = SyntaxElementType("C_STYLE_COMMENT")
  val END_OF_LINE_COMMENT: SyntaxElementType = SyntaxElementType("END_OF_LINE_COMMENT")

  val INTEGER_LITERAL: SyntaxElementType = SyntaxElementType("INTEGER_LITERAL")
  val LONG_LITERAL: SyntaxElementType = SyntaxElementType("LONG_LITERAL")
  val FLOAT_LITERAL: SyntaxElementType = SyntaxElementType("FLOAT_LITERAL")
  val DOUBLE_LITERAL: SyntaxElementType = SyntaxElementType("DOUBLE_LITERAL")
  val CHARACTER_LITERAL: SyntaxElementType = SyntaxElementType("CHARACTER_LITERAL")
  val STRING_LITERAL: SyntaxElementType = SyntaxElementType("STRING_LITERAL")
  val TEXT_BLOCK_LITERAL: SyntaxElementType = SyntaxElementType("TEXT_BLOCK_LITERAL")

  val STRING_TEMPLATE_BEGIN: SyntaxElementType = SyntaxElementType("STRING_TEMPLATE_BEGIN")
  val STRING_TEMPLATE_MID: SyntaxElementType = SyntaxElementType("STRING_TEMPLATE_MID")
  val STRING_TEMPLATE_END: SyntaxElementType = SyntaxElementType("STRING_TEMPLATE_END")
  val TEXT_BLOCK_TEMPLATE_BEGIN: SyntaxElementType = SyntaxElementType("TEXT_BLOCK_TEMPLATE_BEGIN")
  val TEXT_BLOCK_TEMPLATE_MID: SyntaxElementType = SyntaxElementType("TEXT_BLOCK_TEMPLATE_MID")
  val TEXT_BLOCK_TEMPLATE_END: SyntaxElementType = SyntaxElementType("TEXT_BLOCK_TEMPLATE_END")

  val TRUE_KEYWORD: SyntaxElementType = SyntaxElementType("TRUE_KEYWORD")
  val FALSE_KEYWORD: SyntaxElementType = SyntaxElementType("FALSE_KEYWORD")
  val NULL_KEYWORD: SyntaxElementType = SyntaxElementType("NULL_KEYWORD")

  val ABSTRACT_KEYWORD: SyntaxElementType = SyntaxElementType("ABSTRACT_KEYWORD")
  val ASSERT_KEYWORD: SyntaxElementType = SyntaxElementType("ASSERT_KEYWORD")
  val BOOLEAN_KEYWORD: SyntaxElementType = SyntaxElementType("BOOLEAN_KEYWORD")
  val BREAK_KEYWORD: SyntaxElementType = SyntaxElementType("BREAK_KEYWORD")
  val BYTE_KEYWORD: SyntaxElementType = SyntaxElementType("BYTE_KEYWORD")
  val CASE_KEYWORD: SyntaxElementType = SyntaxElementType("CASE_KEYWORD")
  val CATCH_KEYWORD: SyntaxElementType = SyntaxElementType("CATCH_KEYWORD")
  val CHAR_KEYWORD: SyntaxElementType = SyntaxElementType("CHAR_KEYWORD")
  val CLASS_KEYWORD: SyntaxElementType = SyntaxElementType("CLASS_KEYWORD")
  val CONST_KEYWORD: SyntaxElementType = SyntaxElementType("CONST_KEYWORD")
  val CONTINUE_KEYWORD: SyntaxElementType = SyntaxElementType("CONTINUE_KEYWORD")
  val DEFAULT_KEYWORD: SyntaxElementType = SyntaxElementType("DEFAULT_KEYWORD")
  val DO_KEYWORD: SyntaxElementType = SyntaxElementType("DO_KEYWORD")
  val DOUBLE_KEYWORD: SyntaxElementType = SyntaxElementType("DOUBLE_KEYWORD")
  val ELSE_KEYWORD: SyntaxElementType = SyntaxElementType("ELSE_KEYWORD")
  val ENUM_KEYWORD: SyntaxElementType = SyntaxElementType("ENUM_KEYWORD")
  val EXTENDS_KEYWORD: SyntaxElementType = SyntaxElementType("EXTENDS_KEYWORD")
  val FINAL_KEYWORD: SyntaxElementType = SyntaxElementType("FINAL_KEYWORD")
  val FINALLY_KEYWORD: SyntaxElementType = SyntaxElementType("FINALLY_KEYWORD")
  val FLOAT_KEYWORD: SyntaxElementType = SyntaxElementType("FLOAT_KEYWORD")
  val FOR_KEYWORD: SyntaxElementType = SyntaxElementType("FOR_KEYWORD")
  val GOTO_KEYWORD: SyntaxElementType = SyntaxElementType("GOTO_KEYWORD")
  val IF_KEYWORD: SyntaxElementType = SyntaxElementType("IF_KEYWORD")
  val IMPLEMENTS_KEYWORD: SyntaxElementType = SyntaxElementType("IMPLEMENTS_KEYWORD")
  val IMPORT_KEYWORD: SyntaxElementType = SyntaxElementType("IMPORT_KEYWORD")
  val INSTANCEOF_KEYWORD: SyntaxElementType = SyntaxElementType("INSTANCEOF_KEYWORD")
  val INT_KEYWORD: SyntaxElementType = SyntaxElementType("INT_KEYWORD")
  val INTERFACE_KEYWORD: SyntaxElementType = SyntaxElementType("INTERFACE_KEYWORD")
  val LONG_KEYWORD: SyntaxElementType = SyntaxElementType("LONG_KEYWORD")
  val NATIVE_KEYWORD: SyntaxElementType = SyntaxElementType("NATIVE_KEYWORD")
  val NEW_KEYWORD: SyntaxElementType = SyntaxElementType("NEW_KEYWORD")
  val PACKAGE_KEYWORD: SyntaxElementType = SyntaxElementType("PACKAGE_KEYWORD")
  val PRIVATE_KEYWORD: SyntaxElementType = SyntaxElementType("PRIVATE_KEYWORD")
  val PUBLIC_KEYWORD: SyntaxElementType = SyntaxElementType("PUBLIC_KEYWORD")
  val SHORT_KEYWORD: SyntaxElementType = SyntaxElementType("SHORT_KEYWORD")
  val SUPER_KEYWORD: SyntaxElementType = SyntaxElementType("SUPER_KEYWORD")
  val SWITCH_KEYWORD: SyntaxElementType = SyntaxElementType("SWITCH_KEYWORD")
  val SYNCHRONIZED_KEYWORD: SyntaxElementType = SyntaxElementType("SYNCHRONIZED_KEYWORD")
  val THIS_KEYWORD: SyntaxElementType = SyntaxElementType("THIS_KEYWORD")
  val THROW_KEYWORD: SyntaxElementType = SyntaxElementType("THROW_KEYWORD")
  val PROTECTED_KEYWORD: SyntaxElementType = SyntaxElementType("PROTECTED_KEYWORD")
  val TRANSIENT_KEYWORD: SyntaxElementType = SyntaxElementType("TRANSIENT_KEYWORD")
  val RETURN_KEYWORD: SyntaxElementType = SyntaxElementType("RETURN_KEYWORD")
  val VOID_KEYWORD: SyntaxElementType = SyntaxElementType("VOID_KEYWORD")
  val STATIC_KEYWORD: SyntaxElementType = SyntaxElementType("STATIC_KEYWORD")
  val STRICTFP_KEYWORD: SyntaxElementType = SyntaxElementType("STRICTFP_KEYWORD")
  val WHILE_KEYWORD: SyntaxElementType = SyntaxElementType("WHILE_KEYWORD")
  val TRY_KEYWORD: SyntaxElementType = SyntaxElementType("TRY_KEYWORD")
  val VOLATILE_KEYWORD: SyntaxElementType = SyntaxElementType("VOLATILE_KEYWORD")
  val THROWS_KEYWORD: SyntaxElementType = SyntaxElementType("THROWS_KEYWORD")

  val LPARENTH: SyntaxElementType = SyntaxElementType("LPARENTH")
  val RPARENTH: SyntaxElementType = SyntaxElementType("RPARENTH")
  val LBRACE: SyntaxElementType = SyntaxElementType("LBRACE")
  val RBRACE: SyntaxElementType = SyntaxElementType("RBRACE")
  val LBRACKET: SyntaxElementType = SyntaxElementType("LBRACKET")
  val RBRACKET: SyntaxElementType = SyntaxElementType("RBRACKET")
  val SEMICOLON: SyntaxElementType = SyntaxElementType("SEMICOLON")
  val COMMA: SyntaxElementType = SyntaxElementType("COMMA")
  val DOT: SyntaxElementType = SyntaxElementType("DOT")
  val ELLIPSIS: SyntaxElementType = SyntaxElementType("ELLIPSIS")
  val AT: SyntaxElementType = SyntaxElementType("AT")

  val EQ: SyntaxElementType = SyntaxElementType("EQ")
  val GT: SyntaxElementType = SyntaxElementType("GT")
  val LT: SyntaxElementType = SyntaxElementType("LT")
  val EXCL: SyntaxElementType = SyntaxElementType("EXCL")
  val TILDE: SyntaxElementType = SyntaxElementType("TILDE")
  val QUEST: SyntaxElementType = SyntaxElementType("QUEST")
  val COLON: SyntaxElementType = SyntaxElementType("COLON")
  val PLUS: SyntaxElementType = SyntaxElementType("PLUS")
  val MINUS: SyntaxElementType = SyntaxElementType("MINUS")
  val ASTERISK: SyntaxElementType = SyntaxElementType("ASTERISK")
  val DIV: SyntaxElementType = SyntaxElementType("DIV")
  val AND: SyntaxElementType = SyntaxElementType("AND")
  val OR: SyntaxElementType = SyntaxElementType("OR")
  val XOR: SyntaxElementType = SyntaxElementType("XOR")
  val PERC: SyntaxElementType = SyntaxElementType("PERC")

  val EQEQ: SyntaxElementType = SyntaxElementType("EQEQ")
  val LE: SyntaxElementType = SyntaxElementType("LE")
  val GE: SyntaxElementType = SyntaxElementType("GE")
  val NE: SyntaxElementType = SyntaxElementType("NE")
  val ANDAND: SyntaxElementType = SyntaxElementType("ANDAND")
  val OROR: SyntaxElementType = SyntaxElementType("OROR")
  val PLUSPLUS: SyntaxElementType = SyntaxElementType("PLUSPLUS")
  val MINUSMINUS: SyntaxElementType = SyntaxElementType("MINUSMINUS")
  val LTLT: SyntaxElementType = SyntaxElementType("LTLT")
  val GTGT: SyntaxElementType = SyntaxElementType("GTGT")
  val GTGTGT: SyntaxElementType = SyntaxElementType("GTGTGT")
  val PLUSEQ: SyntaxElementType = SyntaxElementType("PLUSEQ")
  val MINUSEQ: SyntaxElementType = SyntaxElementType("MINUSEQ")
  val ASTERISKEQ: SyntaxElementType = SyntaxElementType("ASTERISKEQ")
  val DIVEQ: SyntaxElementType = SyntaxElementType("DIVEQ")
  val ANDEQ: SyntaxElementType = SyntaxElementType("ANDEQ")
  val OREQ: SyntaxElementType = SyntaxElementType("OREQ")
  val XOREQ: SyntaxElementType = SyntaxElementType("XOREQ")
  val PERCEQ: SyntaxElementType = SyntaxElementType("PERCEQ")
  val LTLTEQ: SyntaxElementType = SyntaxElementType("LTLTEQ")
  val GTGTEQ: SyntaxElementType = SyntaxElementType("GTGTEQ")
  val GTGTGTEQ: SyntaxElementType = SyntaxElementType("GTGTGTEQ")

  val DOUBLE_COLON: SyntaxElementType = SyntaxElementType("DOUBLE_COLON")
  val ARROW: SyntaxElementType = SyntaxElementType("ARROW")

  val OPEN_KEYWORD: SyntaxElementType = SyntaxElementType("OPEN")
  val MODULE_KEYWORD: SyntaxElementType = SyntaxElementType("MODULE")
  val REQUIRES_KEYWORD: SyntaxElementType = SyntaxElementType("REQUIRES")
  val EXPORTS_KEYWORD: SyntaxElementType = SyntaxElementType("EXPORTS")
  val OPENS_KEYWORD: SyntaxElementType = SyntaxElementType("OPENS")
  val USES_KEYWORD: SyntaxElementType = SyntaxElementType("USES")
  val PROVIDES_KEYWORD: SyntaxElementType = SyntaxElementType("PROVIDES")
  val TRANSITIVE_KEYWORD: SyntaxElementType = SyntaxElementType("TRANSITIVE")
  val TO_KEYWORD: SyntaxElementType = SyntaxElementType("TO")
  val WITH_KEYWORD: SyntaxElementType = SyntaxElementType("WITH")

  val VAR_KEYWORD: SyntaxElementType = SyntaxElementType("VAR")
  val YIELD_KEYWORD: SyntaxElementType = SyntaxElementType("YIELD")
  val RECORD_KEYWORD: SyntaxElementType = SyntaxElementType("RECORD")

  val VALUE_KEYWORD: SyntaxElementType = SyntaxElementType("VALUE_KEYWORD")
  val SEALED_KEYWORD: SyntaxElementType = SyntaxElementType("SEALED")
  val NON_SEALED_KEYWORD: SyntaxElementType = SyntaxElementType("NON_SEALED")
  val PERMITS_KEYWORD: SyntaxElementType = SyntaxElementType("PERMITS")
  val WHEN_KEYWORD: SyntaxElementType = SyntaxElementType("WHEN_KEYWORD")

  val JAVA_TOKEN_TYPE_SET: SyntaxElementTypeSet = syntaxElementTypeSetOf(
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