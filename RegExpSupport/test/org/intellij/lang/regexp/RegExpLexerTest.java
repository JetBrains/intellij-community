// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp;

import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

import static org.intellij.lang.regexp.RegExpCapability.ALLOW_EMPTY_CHARACTER_CLASS;
import static org.intellij.lang.regexp.RegExpCapability.COMMENT_MODE;
import static org.intellij.lang.regexp.RegExpCapability.DANGLING_METACHARACTERS;
import static org.intellij.lang.regexp.RegExpCapability.EXTENDED_UNICODE_CHARACTER;
import static org.intellij.lang.regexp.RegExpCapability.MAX_OCTAL_177;
import static org.intellij.lang.regexp.RegExpCapability.MAX_OCTAL_377;
import static org.intellij.lang.regexp.RegExpCapability.MIN_OCTAL_2_DIGITS;
import static org.intellij.lang.regexp.RegExpCapability.MIN_OCTAL_3_DIGITS;
import static org.intellij.lang.regexp.RegExpCapability.MYSQL_BRACKET_EXPRESSIONS;
import static org.intellij.lang.regexp.RegExpCapability.NESTED_CHARACTER_CLASSES;
import static org.intellij.lang.regexp.RegExpCapability.OCTAL_NO_LEADING_ZERO;
import static org.intellij.lang.regexp.RegExpCapability.OMIT_BOTH_NUMBERS_IN_QUANTIFIERS;
import static org.intellij.lang.regexp.RegExpCapability.OMIT_NUMBERS_IN_QUANTIFIERS;
import static org.intellij.lang.regexp.RegExpCapability.ONE_HEX_CHAR_ESCAPE;
import static org.intellij.lang.regexp.RegExpCapability.PCRE_BACK_REFERENCES;
import static org.intellij.lang.regexp.RegExpCapability.PCRE_CONDITIONS;
import static org.intellij.lang.regexp.RegExpCapability.PCRE_NUMBERED_GROUP_REF;
import static org.intellij.lang.regexp.RegExpCapability.POSIX_BRACKET_EXPRESSIONS;
import static org.intellij.lang.regexp.RegExpCapability.TRANSFORMATION_ESCAPES;
import static org.intellij.lang.regexp.RegExpCapability.WHITESPACE_IN_CLASS;

/**
 * @author Bas Leijdekkers
 */
public class RegExpLexerTest extends LexerTestCase.WithLexerFactory {
  public void testAtomicGroup() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("(?>atom)", """
      ATOMIC_GROUP ('(?>')
      CHARACTER ('a')
      CHARACTER ('t')
      CHARACTER ('o')
      CHARACTER ('m')
      GROUP_END (')')""");
    doTest("(?:no)", """
      NON_CAPT_GROUP ('(?:')
      CHARACTER ('n')
      CHARACTER ('o')
      GROUP_END (')')""");
  }

  public void testAmpersand() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("[a&&]", """
      CLASS_BEGIN ('[')
      CHARACTER ('a')
      CHARACTER ('&')
      CHARACTER ('&')
      CLASS_END (']')""");
  }

  public void testQE() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("\\Q\r\n\\E", """
      QUOTE_BEGIN ('\\Q')
      CHARACTER ('
      ')
      CHARACTER ('\\n')
      QUOTE_END ('\\E')""");
  }

  public void testEditorReplacement() {
    final String text = "\\U$1\\E\\u$3\\l$4\\L$2\\E";
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(TRANSFORMATION_ESCAPES)));
    doTest(text, """
      CHAR_CLASS ('\\U')
      DOLLAR ('$')
      CHARACTER ('1')
      CHAR_CLASS ('\\E')
      CHAR_CLASS ('\\u')
      DOLLAR ('$')
      CHARACTER ('3')
      CHAR_CLASS ('\\l')
      DOLLAR ('$')
      CHARACTER ('4')
      CHAR_CLASS ('\\L')
      DOLLAR ('$')
      CHARACTER ('2')
      CHAR_CLASS ('\\E')""");

    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest(text, """
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\U')
      DOLLAR ('$')
      CHARACTER ('1')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\E')
      INVALID_UNICODE_ESCAPE_TOKEN ('\\u')
      DOLLAR ('$')
      CHARACTER ('3')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\l')
      DOLLAR ('$')
      CHARACTER ('4')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\L')
      DOLLAR ('$')
      CHARACTER ('2')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\E')""");
  }

  public void testIntersection() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(NESTED_CHARACTER_CLASSES)));
    doTest("[a&&]", """
      CLASS_BEGIN ('[')
      CHARACTER ('a')
      ANDAND ('&&')
      CLASS_END (']')""");
  }

  public void testCarets() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("^\\^[^^]", """
      CARET ('^')
      ESC_CHARACTER ('\\^')
      CLASS_BEGIN ('[')
      CARET ('^')
      CHARACTER ('^')
      CLASS_END (']')""");
  }

  public void testPosixBracketExpression() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(POSIX_BRACKET_EXPRESSIONS)));
    doTest("[[:xdigit:]]", """
      CLASS_BEGIN ('[')
      BRACKET_EXPRESSION_BEGIN ('[:')
      NAME ('xdigit')
      BRACKET_EXPRESSION_END (':]')
      CLASS_END (']')""");
  }

  public void testNegatedPosixBracketExpression() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(POSIX_BRACKET_EXPRESSIONS)));
    doTest("[[:^xdigit:]]", """
      CLASS_BEGIN ('[')
      BRACKET_EXPRESSION_BEGIN ('[:')
      CARET ('^')
      NAME ('xdigit')
      BRACKET_EXPRESSION_END (':]')
      CLASS_END (']')""");
  }

  public void testMysqlCharExpressions() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(MYSQL_BRACKET_EXPRESSIONS)));
    doTest("[[.~.][.tilda.][.NUL.][.plus-sign.]]", """
      CLASS_BEGIN ('[')
      MYSQL_CHAR_BEGIN ('[.')
      CHARACTER ('~')
      MYSQL_CHAR_END ('.]')
      MYSQL_CHAR_BEGIN ('[.')
      NAME ('tilda')
      MYSQL_CHAR_END ('.]')
      MYSQL_CHAR_BEGIN ('[.')
      NAME ('NUL')
      MYSQL_CHAR_END ('.]')
      MYSQL_CHAR_BEGIN ('[.')
      NAME ('plus-sign')
      MYSQL_CHAR_END ('.]')
      CLASS_END (']')""");
  }

  public void testMysqlCharEqExpressions() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(MYSQL_BRACKET_EXPRESSIONS)));
    doTest("[[=.=][=c=]]", """
      CLASS_BEGIN ('[')
      MYSQL_CHAR_EQ_BEGIN ('[=')
      CHARACTER ('.')
      MYSQL_CHAR_EQ_END ('=]')
      MYSQL_CHAR_EQ_BEGIN ('[=')
      CHARACTER ('c')
      MYSQL_CHAR_EQ_END ('=]')
      CLASS_END (']')""");
  }

  /**
   * \\177 is the maximum valid octal character under Ruby.
   */
  public void testMaxOctalNoLeadingZero1() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MAX_OCTAL_177)));
    doTest("\\177\\200", """
      OCT_CHAR ('\\177')
      BAD_OCT_VALUE ('\\20')
      CHARACTER ('0')""");
  }

  /**
   * \\377 is the maximum valid octal character under javascript. \\400 is interpreted as \\40 followed by a 0 character.
   * The BAD_OCT_VALUE token is converted to OCT_CHAR in com.intellij.lang.javascript.inject.JSRegexpParserDefinition
   */
  public void testMaxOctalNoLeadingZero2() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MAX_OCTAL_377)));
    doTest("\\177\\200\\377\\400", """
      OCT_CHAR ('\\177')
      OCT_CHAR ('\\200')
      OCT_CHAR ('\\377')
      BAD_OCT_VALUE ('\\40')
      CHARACTER ('0')""");
  }

  /**
   * \\777 is valid octal character in python regex dialect.
   */
  public void testMaxOctalNoLeadingZero3() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO)));
    doTest("\\177\\200\\377\\400\\777", """
      OCT_CHAR ('\\177')
      OCT_CHAR ('\\200')
      OCT_CHAR ('\\377')
      OCT_CHAR ('\\400')
      OCT_CHAR ('\\777')""");
  }

  /**
   * \\1 and \\11 valid under js, both inside and outside character class
   */
  public void testOctalNoLeadingZero1() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO)));
    doTest("\\1()\\1\\11[\\1\\11]", """
      OCT_CHAR ('\\1')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      BACKREF ('\\1')
      OCT_CHAR ('\\11')
      CLASS_BEGIN ('[')
      OCT_CHAR ('\\1')
      OCT_CHAR ('\\11')
      CLASS_END (']')""");
  }

  /**
   * \\1 not valid and \\11 valid under ruby, outside character class
   */
  public void testOctalNoLeadingZero2() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MIN_OCTAL_2_DIGITS)));
    doTest("\\1()\\1\\11[\\1\\11]", """
      BAD_OCT_VALUE ('\\1')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      BACKREF ('\\1')
      OCT_CHAR ('\\11')
      CLASS_BEGIN ('[')
      OCT_CHAR ('\\1')
      OCT_CHAR ('\\11')
      CLASS_END (']')""");
  }

  /**
   * \\1 and \\11 not valid under python, outside character class
   */
  public void testOctalNoLeadingZero3() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MIN_OCTAL_3_DIGITS)));
    doTest("\\1()\\1\\11\\111[\\1\\11\\111]", """
      BAD_OCT_VALUE ('\\1')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      BACKREF ('\\1')
      BAD_OCT_VALUE ('\\11')
      OCT_CHAR ('\\111')
      CLASS_BEGIN ('[')
      OCT_CHAR ('\\1')
      OCT_CHAR ('\\11')
      OCT_CHAR ('\\111')
      CLASS_END (']')""");
  }


  /** octal is never a back reference inside a character class, valid under js, ruby, python */
  public void testOctalInsideCharClass() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO)));
    doTest("()()()()()()()()()()[\\1\\10\\100]", """
      GROUP_BEGIN ('(')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      CLASS_BEGIN ('[')
      OCT_CHAR ('\\1')
      OCT_CHAR ('\\10')
      OCT_CHAR ('\\100')
      CLASS_END (']')""");
  }

  /** \0 always valid under js, ruby, python regex dialects, never a back reference. */
  public void testZeroOctalNoLeadingZero() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO)));
    doTest("\\0()\\0[\\0]", """
      OCT_CHAR ('\\0')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      OCT_CHAR ('\\0')
      CLASS_BEGIN ('[')
      OCT_CHAR ('\\0')
      CLASS_END (']')""");
  }

  /** three digit octal (\100) always valid, either octal or backreference under js, ruby and python */
  public void testThreeDigitOctalNoLeadingZero() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
    String result = printTokens(lexer, "\\100" +
                                       "()()()()()()()()()()" +
                                       "()()()()()()()()()()" +
                                       "()()()()()()()()()()" +
                                       "()()()()()()()()()()" +
                                       "()()()()()()()()()()" +
                                       "()()()()()()()()()()" +
                                       "()()()()()()()()()()" +
                                       "()()()()()()()()()()" +
                                       "()()()()()()()()()()" +
                                       "()()()()()()()()()()\\100[\\100]", 0);
    
    assertTrue(result.endsWith("""
                                 BACKREF ('\\100')
                                 CLASS_BEGIN ('[')
                                 OCT_CHAR ('\\100')
                                 CLASS_END (']')
                                 """));
  }

  public void testOctalFollowedByDigit() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO)));
    doTest("\\39[\\39]", """
      OCT_CHAR ('\\3')
      CHARACTER ('9')
      CLASS_BEGIN ('[')
      OCT_CHAR ('\\3')
      CHARACTER ('9')
      CLASS_END (']')""");
  }

  public void testOctalWithLeadingZero() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("\\0\\123[\\123]", """
      BAD_OCT_VALUE ('\\0')
      BACKREF ('\\1')
      CHARACTER ('2')
      CHARACTER ('3')
      CLASS_BEGIN ('[')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\1')
      CHARACTER ('2')
      CHARACTER ('3')
      CLASS_END (']')""");
  }

  public void testOctalWithLeadingZero2() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("\\08\\01\\00\\012\\0123\\0377\\0400", """
      BAD_OCT_VALUE ('\\0')
      CHARACTER ('8')
      OCT_CHAR ('\\01')
      OCT_CHAR ('\\00')
      OCT_CHAR ('\\012')
      OCT_CHAR ('\\0123')
      OCT_CHAR ('\\0377')
      OCT_CHAR ('\\040')
      CHARACTER ('0')""");
  }

  public void testBackReference() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\105", """
      GROUP_BEGIN ('(')
      CHARACTER ('a')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      CHARACTER ('b')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      CHARACTER ('c')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      CHARACTER ('d')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      CHARACTER ('e')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      CHARACTER ('f')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      CHARACTER ('g')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      CHARACTER ('h')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      CHARACTER ('i')
      GROUP_END (')')
      GROUP_BEGIN ('(')
      CHARACTER ('j')
      GROUP_END (')')
      BACKREF ('\\10')
      CHARACTER ('5')""");
  }

  public void testPcreBackReference() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(PCRE_BACK_REFERENCES)));
    doTest("(a)\\g105", """
      GROUP_BEGIN ('(')
      CHARACTER ('a')
      GROUP_END (')')
      BACKREF ('\\g105')""");
  }

  public void testPcreRelativeBackReference() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(PCRE_BACK_REFERENCES)));
    doTest("(a)\\g{105}", """
      GROUP_BEGIN ('(')
      CHARACTER ('a')
      GROUP_END (')')
      BACKREF ('\\g{105}')""");
  }

  public void testPcreRelativeNegativeBackReference() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(PCRE_BACK_REFERENCES)));
    doTest("(a)\\g{-105}", """
      GROUP_BEGIN ('(')
      CHARACTER ('a')
      GROUP_END (')')
      BACKREF ('\\g{-105}')""");
  }

  public void testPcreRelativeNegativeInvalidBackReference() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(PCRE_BACK_REFERENCES)));
    doTest("(a)\\g-105", """
      GROUP_BEGIN ('(')
      CHARACTER ('a')
      GROUP_END (')')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\g')
      CHARACTER ('-')
      CHARACTER ('1')
      CHARACTER ('0')
      CHARACTER ('5')""");
  }

  public void testPcreConditionDefine() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(PCRE_CONDITIONS)));
    doTest("(?(DEFINE)(?<Name>\\w+))(?P>Name)", """
      CONDITIONAL ('(?')
      GROUP_BEGIN ('(')
      PCRE_DEFINE ('DEFINE')
      GROUP_END (')')
      RUBY_NAMED_GROUP ('(?<')
      NAME ('Name')
      GT ('>')
      CHAR_CLASS ('\\w')
      PLUS ('+')
      GROUP_END (')')
      GROUP_END (')')
      PCRE_RECURSIVE_NAMED_GROUP ('(?P>')
      NAME ('Name')
      GROUP_END (')')""");
  }

  public void testPcreConditionVersion() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(PCRE_CONDITIONS)));
    doTest("(?(VERSION>=10.7)yes|no)", """
      CONDITIONAL ('(?')
      GROUP_BEGIN ('(')
      PCRE_VERSION ('VERSION>=10.7')
      GROUP_END (')')
      CHARACTER ('y')
      CHARACTER ('e')
      CHARACTER ('s')
      UNION ('|')
      CHARACTER ('n')
      CHARACTER ('o')
      GROUP_END (')')""");
  }

  public void testNoPcreCondition() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("(?(DEFINE)(?<Name>\\w+))(?P>Name)", """
      CONDITIONAL ('(?')
      GROUP_BEGIN ('(')
      NAME ('DEFINE')
      GROUP_END (')')
      RUBY_NAMED_GROUP ('(?<')
      NAME ('Name')
      GT ('>')
      CHAR_CLASS ('\\w')
      PLUS ('+')
      GROUP_END (')')
      GROUP_END (')')
      PCRE_RECURSIVE_NAMED_GROUP ('(?P>')
      NAME ('Name')
      GROUP_END (')')""");
  }

  public void testNoNestedCharacterClasses1() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("[[\\]]", """
      CLASS_BEGIN ('[')
      CHARACTER ('[')
      ESC_CHARACTER ('\\]')
      CLASS_END (']')""");
  }

  public void testNoNestedCharacterClasses2() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("[a-z&&[^aeuoi]]", """
      CLASS_BEGIN ('[')
      CHARACTER ('a')
      MINUS ('-')
      CHARACTER ('z')
      CHARACTER ('&')
      CHARACTER ('&')
      CHARACTER ('[')
      CHARACTER ('^')
      CHARACTER ('a')
      CHARACTER ('e')
      CHARACTER ('u')
      CHARACTER ('o')
      CHARACTER ('i')
      CLASS_END (']')
      CHARACTER (']')""");
  }

  public void testNestedCharacterClasses1() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(NESTED_CHARACTER_CLASSES)));
    doTest("[a-z&&[^aeuoi]]", """
      CLASS_BEGIN ('[')
      CHARACTER ('a')
      MINUS ('-')
      CHARACTER ('z')
      ANDAND ('&&')
      CLASS_BEGIN ('[')
      CARET ('^')
      CHARACTER ('a')
      CHARACTER ('e')
      CHARACTER ('u')
      CHARACTER ('o')
      CHARACTER ('i')
      CLASS_END (']')
      CLASS_END (']')""");
  }

  public void testNestedCharacterClasses2() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(NESTED_CHARACTER_CLASSES)));
    doTest("[]]", """
      CLASS_BEGIN ('[')
      CHARACTER (']')
      CLASS_END (']')""");
    doTest("[\\]]", """
      CLASS_BEGIN ('[')
      REDUNDANT_ESCAPE ('\\]')
      CLASS_END (']')""");
    doTest("[[]]]", """
      CLASS_BEGIN ('[')
      CLASS_BEGIN ('[')
      CHARACTER (']')
      CLASS_END (']')
      CLASS_END (']')""");
    doTest("[ \\]]", """
      CLASS_BEGIN ('[')
      CHARACTER (' ')
      ESC_CHARACTER ('\\]')
      CLASS_END (']')""");
    doTest("[\\Q\\E]]", """
      CLASS_BEGIN ('[')
      QUOTE_BEGIN ('\\Q')
      QUOTE_END ('\\E')
      CHARACTER (']')
      CLASS_END (']')""");
    doTest("[\\Q+\\E]]", """
      CLASS_BEGIN ('[')
      QUOTE_BEGIN ('\\Q')
      CHARACTER ('+')
      QUOTE_END ('\\E')
      CLASS_END (']')
      CHARACTER (']')""");
    doTest("[^\\Q\\E]]", """
      CLASS_BEGIN ('[')
      CARET ('^')
      QUOTE_BEGIN ('\\Q')
      QUOTE_END ('\\E')
      CHARACTER (']')
      CLASS_END (']')""");
    doTest("[^\\Q+\\E]]", """
      CLASS_BEGIN ('[')
      CARET ('^')
      QUOTE_BEGIN ('\\Q')
      CHARACTER ('+')
      QUOTE_END ('\\E')
      CLASS_END (']')
      CHARACTER (']')""");
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(COMMENT_MODE, WHITESPACE_IN_CLASS)));
    doTest("[ \t\n]]", """
      CLASS_BEGIN ('[')
      WHITE_SPACE (' ')
      WHITE_SPACE ('\t')
      WHITE_SPACE ('\\n')
      CHARACTER (']')
      CLASS_END (']')""");
    doTest("[\\ ]", """
      CLASS_BEGIN ('[')
      ESC_CTRL_CHARACTER ('\\ ')
      CLASS_END (']')""");
    doTest("[#comment\nabc]", """
      CLASS_BEGIN ('[')
      COMMENT ('#comment')
      WHITE_SPACE ('\\n')
      CHARACTER ('a')
      CHARACTER ('b')
      CHARACTER ('c')
      CLASS_END (']')""");
    doTest("[ ^]]", """
      CLASS_BEGIN ('[')
      WHITE_SPACE (' ')
      CHARACTER ('^')
      CLASS_END (']')
      CHARACTER (']')""");
    doTest("[\\", "CLASS_BEGIN ('[')\n" +
                  "INVALID_CHARACTER_ESCAPE_TOKEN ('\\')");
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(ALLOW_EMPTY_CHARACTER_CLASS)));
    doTest("[]]", """
      CLASS_BEGIN ('[')
      CLASS_END (']')
      CHARACTER (']')""");
    doTest("[[]]]", """
      CLASS_BEGIN ('[')
      CHARACTER ('[')
      CLASS_END (']')
      CHARACTER (']')
      CHARACTER (']')""");
    doTest("[\\]]", """
      CLASS_BEGIN ('[')
      ESC_CHARACTER ('\\]')
      CLASS_END (']')""");
    doTest("[ \\]]", """
      CLASS_BEGIN ('[')
      CHARACTER (' ')
      ESC_CHARACTER ('\\]')
      CLASS_END (']')""");
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(COMMENT_MODE)));
    doTest("[ ]", """
      CLASS_BEGIN ('[')
      CHARACTER (' ')
      CLASS_END (']')""");
    doTest("[#]", """
      CLASS_BEGIN ('[')
      CHARACTER ('#')
      CLASS_END (']')""");
    doTest("[\\ ]", """
      CLASS_BEGIN ('[')
      REDUNDANT_ESCAPE ('\\ ')
      CLASS_END (']')""");
  }

  public void testBoundaries() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("\\b\\b{g}\\B\\A\\z\\Z\\G[\\b\\b{g}\\B\\A\\z\\Z\\G]", """
      BOUNDARY ('\\b')
      BOUNDARY ('\\b{g}')
      BOUNDARY ('\\B')
      BOUNDARY ('\\A')
      BOUNDARY ('\\z')
      BOUNDARY ('\\Z')
      BOUNDARY ('\\G')
      CLASS_BEGIN ('[')
      ESC_CTRL_CHARACTER ('\\b')
      ESC_CTRL_CHARACTER ('\\b')
      CHARACTER ('{')
      CHARACTER ('g')
      CHARACTER ('}')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\B')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\A')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\z')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\Z')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\G')
      CLASS_END (']')""");
  }

  public void testValidEscapes() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("\\%\\ä", "REDUNDANT_ESCAPE ('\\%')\n" +
                     "REDUNDANT_ESCAPE ('\\ä')");

    setLexerFactory(() -> new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS)));
    doTest("{\\}{33,34\\}{1\\}{1,\\}{,\\}{,2\\}", """
      CHARACTER ('{')
      REDUNDANT_ESCAPE ('\\}')
      CHARACTER ('{')
      CHARACTER ('3')
      CHARACTER ('3')
      CHARACTER (',')
      CHARACTER ('3')
      CHARACTER ('4')
      ESC_CHARACTER ('\\}')
      CHARACTER ('{')
      CHARACTER ('1')
      ESC_CHARACTER ('\\}')
      CHARACTER ('{')
      CHARACTER ('1')
      CHARACTER (',')
      ESC_CHARACTER ('\\}')
      CHARACTER ('{')
      CHARACTER (',')
      REDUNDANT_ESCAPE ('\\}')
      CHARACTER ('{')
      CHARACTER (',')
      CHARACTER ('2')
      REDUNDANT_ESCAPE ('\\}')""");

    setLexerFactory(() -> new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS, OMIT_NUMBERS_IN_QUANTIFIERS, OMIT_BOTH_NUMBERS_IN_QUANTIFIERS)));
    doTest("{\\}{,\\}{,2\\}", """
      CHARACTER ('{')
      REDUNDANT_ESCAPE ('\\}')
      CHARACTER ('{')
      CHARACTER (',')
      ESC_CHARACTER ('\\}')
      CHARACTER ('{')
      CHARACTER (',')
      CHARACTER ('2')
      ESC_CHARACTER ('\\}')""");
  }

  public void testEscapesInsideCharClass() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("[\\k<a> (?<t>t)\\g'q'\\R]", """
      CLASS_BEGIN ('[')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\k')
      CHARACTER ('<')
      CHARACTER ('a')
      CHARACTER ('>')
      CHARACTER (' ')
      CHARACTER ('(')
      CHARACTER ('?')
      CHARACTER ('<')
      CHARACTER ('t')
      CHARACTER ('>')
      CHARACTER ('t')
      CHARACTER (')')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\g')
      CHARACTER (''')
      CHARACTER ('q')
      CHARACTER (''')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\R')
      CLASS_END (']')""");

    doTest("\\{\\*\\+\\?\\$[\\{\\*\\+\\?\\$]", """
      ESC_CHARACTER ('\\{')
      ESC_CHARACTER ('\\*')
      ESC_CHARACTER ('\\+')
      ESC_CHARACTER ('\\?')
      ESC_CHARACTER ('\\$')
      CLASS_BEGIN ('[')
      REDUNDANT_ESCAPE ('\\{')
      REDUNDANT_ESCAPE ('\\*')
      REDUNDANT_ESCAPE ('\\+')
      REDUNDANT_ESCAPE ('\\?')
      REDUNDANT_ESCAPE ('\\$')
      CLASS_END (']')""");
  }

  public void testUnicode() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(EXTENDED_UNICODE_CHARACTER)));
    doTest("\\u{1F680}\\x{1F680}\\u{}\\u{1}\\u{FF}\\x{fff}\\u1234\\u123\\u", """
      UNICODE_CHAR ('\\u{1F680}')
      HEX_CHAR ('\\x{1F680}')
      INVALID_UNICODE_ESCAPE_TOKEN ('\\u{}')
      UNICODE_CHAR ('\\u{1}')
      UNICODE_CHAR ('\\u{FF}')
      HEX_CHAR ('\\x{fff}')
      UNICODE_CHAR ('\\u1234')
      INVALID_UNICODE_ESCAPE_TOKEN ('\\u123')
      INVALID_UNICODE_ESCAPE_TOKEN ('\\u')""");
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS)));
    doTest("\\u{1F680}", """
      INVALID_UNICODE_ESCAPE_TOKEN ('\\u')
      CHARACTER ('{')
      CHARACTER ('1')
      CHARACTER ('F')
      CHARACTER ('6')
      CHARACTER ('8')
      CHARACTER ('0')
      CHARACTER ('}')""");
  }

  public void testHexChar() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(ONE_HEX_CHAR_ESCAPE)));
    doTest("\\x\\x1\\x01", """
      BAD_HEX_VALUE ('\\x')
      HEX_CHAR ('\\x1')
      HEX_CHAR ('\\x01')""");
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("\\x\\x1\\x01", """
      BAD_HEX_VALUE ('\\x')
      BAD_HEX_VALUE ('\\x')
      CHARACTER ('1')
      HEX_CHAR ('\\x01')""");
  }

  public void testQuantifier() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS, OMIT_NUMBERS_IN_QUANTIFIERS)));
    doTest("a{,10}", """
      CHARACTER ('a')
      LBRACE ('{')
      COMMA (',')
      NUMBER ('10')
      RBRACE ('}')""");

    doTest("a{10,}", """
      CHARACTER ('a')
      LBRACE ('{')
      NUMBER ('10')
      COMMA (',')
      RBRACE ('}')""");

    doTest("a{", "CHARACTER ('a')\n" +
                 "CHARACTER ('{')");

    doTest("a{1", """
      CHARACTER ('a')
      CHARACTER ('{')
      CHARACTER ('1')""");

    doTest("a{1,", """
      CHARACTER ('a')
      CHARACTER ('{')
      CHARACTER ('1')
      CHARACTER (',')""");

    doTest("a{,,}", """
      CHARACTER ('a')
      CHARACTER ('{')
      CHARACTER (',')
      CHARACTER (',')
      CHARACTER ('}')""");

    doTest("[{1,2}]", """
      CLASS_BEGIN ('[')
      CHARACTER ('{')
      CHARACTER ('1')
      CHARACTER (',')
      CHARACTER ('2')
      CHARACTER ('}')
      CLASS_END (']')""");

    doTest("x\\{9}", """
      CHARACTER ('x')
      ESC_CHARACTER ('\\{')
      CHARACTER ('9')
      CHARACTER ('}')""");

    doTest("[x\\{9}]", """
      CLASS_BEGIN ('[')
      CHARACTER ('x')
      REDUNDANT_ESCAPE ('\\{')
      CHARACTER ('9')
      CHARACTER ('}')
      CLASS_END (']')""");

    doTest("x\\{}", """
      CHARACTER ('x')
      REDUNDANT_ESCAPE ('\\{')
      CHARACTER ('}')""");

    doTest("x{,}", """
      CHARACTER ('x')
      CHARACTER ('{')
      CHARACTER (',')
      CHARACTER ('}')""");

    doTest("x\\{,}", """
      CHARACTER ('x')
      REDUNDANT_ESCAPE ('\\{')
      CHARACTER (',')
      CHARACTER ('}')""");
  }

  public void testQuantifier2() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS)));
    doTest("a{,10}", """
      CHARACTER ('a')
      CHARACTER ('{')
      CHARACTER (',')
      CHARACTER ('1')
      CHARACTER ('0')
      CHARACTER ('}')""");
  }

  public void testQuantifier3() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS, OMIT_NUMBERS_IN_QUANTIFIERS,
                                                                   OMIT_BOTH_NUMBERS_IN_QUANTIFIERS)));
    doTest("a{,}", """
      CHARACTER ('a')
      LBRACE ('{')
      COMMA (',')
      RBRACE ('}')""");

    doTest("x\\{,}", """
      CHARACTER ('x')
      ESC_CHARACTER ('\\{')
      CHARACTER (',')
      CHARACTER ('}')""");
  }

  public void testControlCharacters() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("\\n\\b\\t\\r\\f[\\n\\b\\t\\r\\f]", """
      ESC_CTRL_CHARACTER ('\\n')
      BOUNDARY ('\\b')
      ESC_CTRL_CHARACTER ('\\t')
      ESC_CTRL_CHARACTER ('\\r')
      ESC_CTRL_CHARACTER ('\\f')
      CLASS_BEGIN ('[')
      ESC_CTRL_CHARACTER ('\\n')
      ESC_CTRL_CHARACTER ('\\b')
      ESC_CTRL_CHARACTER ('\\t')
      ESC_CTRL_CHARACTER ('\\r')
      ESC_CTRL_CHARACTER ('\\f')
      CLASS_END (']')""");
    doTest("\n\t\r\f[\n\t\r\f]", """
      CTRL_CHARACTER ('\\n')
      CTRL_CHARACTER ('\t')
      CTRL_CHARACTER ('
      ')
      CTRL_CHARACTER ('\f')
      CLASS_BEGIN ('[')
      CTRL_CHARACTER ('\\n')
      CTRL_CHARACTER ('\t')
      CTRL_CHARACTER ('
      ')
      CTRL_CHARACTER ('\f')
      CLASS_END (']')""");
  }

  public void testCaret() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("[\\^\\^]\\^", """
      CLASS_BEGIN ('[')
      ESC_CHARACTER ('\\^')
      REDUNDANT_ESCAPE ('\\^')
      CLASS_END (']')
      ESC_CHARACTER ('\\^')""");
  }

  public void testPoundSign() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.noneOf(RegExpCapability.class)));
    doTest("\\#(?x)\\#", """
      REDUNDANT_ESCAPE ('\\#')
      SET_OPTIONS ('(?')
      OPTIONS_ON ('x')
      GROUP_END (')')
      ESC_CHARACTER ('\\#')""");
  }

  public void testNumberedGroupRef() {
    setLexerFactory(() -> new RegExpLexer(EnumSet.of(PCRE_NUMBERED_GROUP_REF)));
    doTest("(abcd)(?1)", """
      GROUP_BEGIN ('(')
      CHARACTER ('a')
      CHARACTER ('b')
      CHARACTER ('c')
      CHARACTER ('d')
      GROUP_END (')')
      PCRE_NUMBERED_GROUP_REF ('(?1)')""");
  }

  @Override
  protected void checkCorrectRestart(@NotNull String text) {
    // NOOP. The test fails if enabled
  }

  @Override
  protected @NotNull String getDirPath() {
    throw new AssertionError();
  }
}
