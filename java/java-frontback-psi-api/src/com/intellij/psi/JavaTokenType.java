// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.java.IJavaElementType;
import com.intellij.psi.tree.java.IKeywordElementType;

/**
 * @see com.intellij.java.syntax.element.JavaSyntaxTokenType
 */
@SuppressWarnings("SpellCheckingInspection")
public interface JavaTokenType extends TokenType {
  IElementType IDENTIFIER = new IJavaElementType("IDENTIFIER");
  IElementType C_STYLE_COMMENT = new IJavaElementType("C_STYLE_COMMENT");
  IElementType END_OF_LINE_COMMENT = new IJavaElementType("END_OF_LINE_COMMENT");

  IElementType INTEGER_LITERAL = new IJavaElementType("INTEGER_LITERAL");
  IElementType LONG_LITERAL = new IJavaElementType("LONG_LITERAL");
  IElementType FLOAT_LITERAL = new IJavaElementType("FLOAT_LITERAL");
  IElementType DOUBLE_LITERAL = new IJavaElementType("DOUBLE_LITERAL");
  IElementType CHARACTER_LITERAL = new IJavaElementType("CHARACTER_LITERAL");
  IElementType STRING_LITERAL = new IJavaElementType("STRING_LITERAL");
  IElementType TEXT_BLOCK_LITERAL = new IJavaElementType("TEXT_BLOCK_LITERAL");

  IElementType STRING_TEMPLATE_BEGIN = new IJavaElementType("STRING_TEMPLATE_BEGIN");
  IElementType STRING_TEMPLATE_MID = new IJavaElementType("STRING_TEMPLATE_MID");
  IElementType STRING_TEMPLATE_END = new IJavaElementType("STRING_TEMPLATE_END");
  IElementType TEXT_BLOCK_TEMPLATE_BEGIN = new IJavaElementType("TEXT_BLOCK_TEMPLATE_BEGIN");
  IElementType TEXT_BLOCK_TEMPLATE_MID = new IJavaElementType("TEXT_BLOCK_TEMPLATE_MID");
  IElementType TEXT_BLOCK_TEMPLATE_END = new IJavaElementType("TEXT_BLOCK_TEMPLATE_END");

  IElementType TRUE_KEYWORD = new IKeywordElementType("TRUE_KEYWORD");
  IElementType FALSE_KEYWORD = new IKeywordElementType("FALSE_KEYWORD");
  IElementType NULL_KEYWORD = new IKeywordElementType("NULL_KEYWORD");

  IElementType ABSTRACT_KEYWORD = new IKeywordElementType("ABSTRACT_KEYWORD");
  IElementType ASSERT_KEYWORD = new IKeywordElementType("ASSERT_KEYWORD");
  IElementType BOOLEAN_KEYWORD = new IKeywordElementType("BOOLEAN_KEYWORD");
  IElementType BREAK_KEYWORD = new IKeywordElementType("BREAK_KEYWORD");
  IElementType BYTE_KEYWORD = new IKeywordElementType("BYTE_KEYWORD");
  IElementType CASE_KEYWORD = new IKeywordElementType("CASE_KEYWORD");
  IElementType CATCH_KEYWORD = new IKeywordElementType("CATCH_KEYWORD");
  IElementType CHAR_KEYWORD = new IKeywordElementType("CHAR_KEYWORD");
  IElementType CLASS_KEYWORD = new IKeywordElementType("CLASS_KEYWORD");
  IElementType CONST_KEYWORD = new IKeywordElementType("CONST_KEYWORD");
  IElementType CONTINUE_KEYWORD = new IKeywordElementType("CONTINUE_KEYWORD");
  IElementType DEFAULT_KEYWORD = new IKeywordElementType("DEFAULT_KEYWORD");
  IElementType DO_KEYWORD = new IKeywordElementType("DO_KEYWORD");
  IElementType DOUBLE_KEYWORD = new IKeywordElementType("DOUBLE_KEYWORD");
  IElementType ELSE_KEYWORD = new IKeywordElementType("ELSE_KEYWORD");
  IElementType ENUM_KEYWORD = new IKeywordElementType("ENUM_KEYWORD");
  IElementType EXTENDS_KEYWORD = new IKeywordElementType("EXTENDS_KEYWORD");
  IElementType FINAL_KEYWORD = new IKeywordElementType("FINAL_KEYWORD");
  IElementType FINALLY_KEYWORD = new IKeywordElementType("FINALLY_KEYWORD");
  IElementType FLOAT_KEYWORD = new IKeywordElementType("FLOAT_KEYWORD");
  IElementType FOR_KEYWORD = new IKeywordElementType("FOR_KEYWORD");
  IElementType GOTO_KEYWORD = new IKeywordElementType("GOTO_KEYWORD");
  IElementType IF_KEYWORD = new IKeywordElementType("IF_KEYWORD");
  IElementType IMPLEMENTS_KEYWORD = new IKeywordElementType("IMPLEMENTS_KEYWORD");
  IElementType IMPORT_KEYWORD = new IKeywordElementType("IMPORT_KEYWORD");
  IElementType INSTANCEOF_KEYWORD = new IKeywordElementType("INSTANCEOF_KEYWORD");
  IElementType INT_KEYWORD = new IKeywordElementType("INT_KEYWORD");
  IElementType INTERFACE_KEYWORD = new IKeywordElementType("INTERFACE_KEYWORD");
  IElementType LONG_KEYWORD = new IKeywordElementType("LONG_KEYWORD");
  IElementType NATIVE_KEYWORD = new IKeywordElementType("NATIVE_KEYWORD");
  IElementType NEW_KEYWORD = new IKeywordElementType("NEW_KEYWORD");
  IElementType PACKAGE_KEYWORD = new IKeywordElementType("PACKAGE_KEYWORD");
  IElementType PRIVATE_KEYWORD = new IKeywordElementType("PRIVATE_KEYWORD");
  IElementType PUBLIC_KEYWORD = new IKeywordElementType("PUBLIC_KEYWORD");
  IElementType SHORT_KEYWORD = new IKeywordElementType("SHORT_KEYWORD");
  IElementType SUPER_KEYWORD = new IKeywordElementType("SUPER_KEYWORD");
  IElementType SWITCH_KEYWORD = new IKeywordElementType("SWITCH_KEYWORD");
  IElementType SYNCHRONIZED_KEYWORD = new IKeywordElementType("SYNCHRONIZED_KEYWORD");
  IElementType THIS_KEYWORD = new IKeywordElementType("THIS_KEYWORD");
  IElementType THROW_KEYWORD = new IKeywordElementType("THROW_KEYWORD");
  IElementType PROTECTED_KEYWORD = new IKeywordElementType("PROTECTED_KEYWORD");
  IElementType TRANSIENT_KEYWORD = new IKeywordElementType("TRANSIENT_KEYWORD");
  IElementType RETURN_KEYWORD = new IKeywordElementType("RETURN_KEYWORD");
  IElementType VOID_KEYWORD = new IKeywordElementType("VOID_KEYWORD");
  IElementType STATIC_KEYWORD = new IKeywordElementType("STATIC_KEYWORD");
  IElementType STRICTFP_KEYWORD = new IKeywordElementType("STRICTFP_KEYWORD");
  IElementType WHILE_KEYWORD = new IKeywordElementType("WHILE_KEYWORD");
  IElementType TRY_KEYWORD = new IKeywordElementType("TRY_KEYWORD");
  IElementType VOLATILE_KEYWORD = new IKeywordElementType("VOLATILE_KEYWORD");
  IElementType THROWS_KEYWORD = new IKeywordElementType("THROWS_KEYWORD");

  IElementType LPARENTH = new IJavaElementType("LPARENTH");
  IElementType RPARENTH = new IJavaElementType("RPARENTH");
  IElementType LBRACE = new IJavaElementType("LBRACE");
  IElementType RBRACE = new IJavaElementType("RBRACE");
  IElementType LBRACKET = new IJavaElementType("LBRACKET");
  IElementType RBRACKET = new IJavaElementType("RBRACKET");
  IElementType SEMICOLON = new IJavaElementType("SEMICOLON");
  IElementType COMMA = new IJavaElementType("COMMA");
  IElementType DOT = new IJavaElementType("DOT");
  IElementType ELLIPSIS = new IJavaElementType("ELLIPSIS");
  IElementType AT = new IJavaElementType("AT");

  IElementType EQ = new IJavaElementType("EQ");
  IElementType GT = new IJavaElementType("GT");
  IElementType LT = new IJavaElementType("LT");
  IElementType EXCL = new IJavaElementType("EXCL");
  IElementType TILDE = new IJavaElementType("TILDE");
  IElementType QUEST = new IJavaElementType("QUEST");
  IElementType COLON = new IJavaElementType("COLON");
  IElementType PLUS = new IJavaElementType("PLUS");
  IElementType MINUS = new IJavaElementType("MINUS");
  IElementType ASTERISK = new IJavaElementType("ASTERISK");
  IElementType DIV = new IJavaElementType("DIV");
  IElementType AND = new IJavaElementType("AND");
  IElementType OR = new IJavaElementType("OR");
  IElementType XOR = new IJavaElementType("XOR");
  IElementType PERC = new IJavaElementType("PERC");

  IElementType EQEQ = new IJavaElementType("EQEQ");
  IElementType LE = new IJavaElementType("LE");
  IElementType GE = new IJavaElementType("GE");
  IElementType NE = new IJavaElementType("NE");
  IElementType ANDAND = new IJavaElementType("ANDAND");
  IElementType OROR = new IJavaElementType("OROR");
  IElementType PLUSPLUS = new IJavaElementType("PLUSPLUS");
  IElementType MINUSMINUS = new IJavaElementType("MINUSMINUS");
  IElementType LTLT = new IJavaElementType("LTLT");
  IElementType GTGT = new IJavaElementType("GTGT");
  IElementType GTGTGT = new IJavaElementType("GTGTGT");
  IElementType PLUSEQ = new IJavaElementType("PLUSEQ");
  IElementType MINUSEQ = new IJavaElementType("MINUSEQ");
  IElementType ASTERISKEQ = new IJavaElementType("ASTERISKEQ");
  IElementType DIVEQ = new IJavaElementType("DIVEQ");
  IElementType ANDEQ = new IJavaElementType("ANDEQ");
  IElementType OREQ = new IJavaElementType("OREQ");
  IElementType XOREQ = new IJavaElementType("XOREQ");
  IElementType PERCEQ = new IJavaElementType("PERCEQ");
  IElementType LTLTEQ = new IJavaElementType("LTLTEQ");
  IElementType GTGTEQ = new IJavaElementType("GTGTEQ");
  IElementType GTGTGTEQ = new IJavaElementType("GTGTGTEQ");

  IElementType DOUBLE_COLON = new IJavaElementType("DOUBLE_COLON");
  IElementType ARROW = new IJavaElementType("ARROW");

  IElementType OPEN_KEYWORD = new IJavaElementType("OPEN");
  IElementType MODULE_KEYWORD = new IJavaElementType("MODULE");
  IElementType REQUIRES_KEYWORD = new IJavaElementType("REQUIRES");
  IElementType EXPORTS_KEYWORD = new IJavaElementType("EXPORTS");
  IElementType OPENS_KEYWORD = new IJavaElementType("OPENS");
  IElementType USES_KEYWORD = new IJavaElementType("USES");
  IElementType PROVIDES_KEYWORD = new IJavaElementType("PROVIDES");
  IElementType TRANSITIVE_KEYWORD = new IJavaElementType("TRANSITIVE");
  IElementType TO_KEYWORD = new IJavaElementType("TO");
  IElementType WITH_KEYWORD = new IJavaElementType("WITH");

  IElementType VAR_KEYWORD = new IJavaElementType("VAR");
  IElementType YIELD_KEYWORD = new IJavaElementType("YIELD");
  IElementType RECORD_KEYWORD = new IJavaElementType("RECORD");

  IElementType VALUE_KEYWORD = new IJavaElementType("VALUE_KEYWORD");
  IElementType SEALED_KEYWORD = new IJavaElementType("SEALED");
  IElementType NON_SEALED_KEYWORD = new IKeywordElementType("NON_SEALED");
  IElementType PERMITS_KEYWORD = new IJavaElementType("PERMITS");
  IElementType WHEN_KEYWORD = new IJavaElementType("WHEN_KEYWORD");

  TokenSet JAVA_TOKEN_TYPE_SET = TokenSet.create(IDENTIFIER,
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
                                                 WHEN_KEYWORD);
}