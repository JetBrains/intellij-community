// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;

import java.io.File;
import java.util.EnumSet;

import static org.intellij.lang.regexp.RegExpCapability.*;

/**
 * @author Bas Leijdekkers
 */
public class RegExpLexerTest extends LexerTestCase {

  public void testAtomicGroup() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("(?>atom)", """
      ATOMIC_GROUP ('(?>')
      CHARACTER ('a')
      CHARACTER ('t')
      CHARACTER ('o')
      CHARACTER ('m')
      GROUP_END (')')""", lexer);
    doTest("(?:no)", """
      NON_CAPT_GROUP ('(?:')
      CHARACTER ('n')
      CHARACTER ('o')
      GROUP_END (')')""", lexer);
  }

  public void testAmpersand() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("[a&&]", """
      CLASS_BEGIN ('[')
      CHARACTER ('a')
      CHARACTER ('&')
      CHARACTER ('&')
      CLASS_END (']')""", lexer);
  }

  public void testQE() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\Q\r\n\\E", """
      QUOTE_BEGIN ('\\Q')
      CHARACTER ('
      ')
      CHARACTER ('\\n')
      QUOTE_END ('\\E')""", lexer);
  }

  public void testEditorReplacement() {
    RegExpLexer lexer = new RegExpLexer(EnumSet.of(TRANSFORMATION_ESCAPES));
    final String text = "\\U$1\\E\\u$3\\l$4\\L$2\\E";
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
      CHAR_CLASS ('\\E')""", lexer);

    lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));

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
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\E')""", lexer);
  }

  public void testIntersection() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(NESTED_CHARACTER_CLASSES));
    doTest("[a&&]", """
      CLASS_BEGIN ('[')
      CHARACTER ('a')
      ANDAND ('&&')
      CLASS_END (']')""", lexer);
  }

  public void testCarets() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("^\\^[^^]", """
      CARET ('^')
      ESC_CHARACTER ('\\^')
      CLASS_BEGIN ('[')
      CARET ('^')
      CHARACTER ('^')
      CLASS_END (']')""", lexer);
  }

  public void testPosixBracketExpression() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(POSIX_BRACKET_EXPRESSIONS));
    doTest("[[:xdigit:]]", """
      CLASS_BEGIN ('[')
      BRACKET_EXPRESSION_BEGIN ('[:')
      NAME ('xdigit')
      BRACKET_EXPRESSION_END (':]')
      CLASS_END (']')""", lexer);
  }

  public void testNegatedPosixBracketExpression() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(POSIX_BRACKET_EXPRESSIONS));
    doTest("[[:^xdigit:]]", """
      CLASS_BEGIN ('[')
      BRACKET_EXPRESSION_BEGIN ('[:')
      CARET ('^')
      NAME ('xdigit')
      BRACKET_EXPRESSION_END (':]')
      CLASS_END (']')""", lexer);
  }

  public void testMysqlCharExpressions() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(MYSQL_BRACKET_EXPRESSIONS));
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
      CLASS_END (']')""", lexer);
  }

  public void testMysqlCharEqExpressions() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(MYSQL_BRACKET_EXPRESSIONS));
    doTest("[[=.=][=c=]]", """
      CLASS_BEGIN ('[')
      MYSQL_CHAR_EQ_BEGIN ('[=')
      CHARACTER ('.')
      MYSQL_CHAR_EQ_END ('=]')
      MYSQL_CHAR_EQ_BEGIN ('[=')
      CHARACTER ('c')
      MYSQL_CHAR_EQ_END ('=]')
      CLASS_END (']')""", lexer);
  }

  /**
   * \\177 is the maximum valid octal character under Ruby.
   */
  public void testMaxOctalNoLeadingZero1() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MAX_OCTAL_177));
    doTest("\\177\\200", """
      OCT_CHAR ('\\177')
      BAD_OCT_VALUE ('\\20')
      CHARACTER ('0')""", lexer);
  }

  /**
   * \\377 is the maximum valid octal character under javascript. \\400 is interpreted as \\40 followed by a 0 character.
   * The BAD_OCT_VALUE token is converted to OCT_CHAR in com.intellij.lang.javascript.inject.JSRegexpParserDefinition
   */
  public void testMaxOctalNoLeadingZero2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MAX_OCTAL_377));
    doTest("\\177\\200\\377\\400", """
      OCT_CHAR ('\\177')
      OCT_CHAR ('\\200')
      OCT_CHAR ('\\377')
      BAD_OCT_VALUE ('\\40')
      CHARACTER ('0')""", lexer);
  }

  /**
   * \\777 is valid octal character in python regex dialect.
   */
  public void testMaxOctalNoLeadingZero3() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
    doTest("\\177\\200\\377\\400\\777", """
      OCT_CHAR ('\\177')
      OCT_CHAR ('\\200')
      OCT_CHAR ('\\377')
      OCT_CHAR ('\\400')
      OCT_CHAR ('\\777')""", lexer);
  }

  /**
   * \\1 and \\11 valid under js, both inside and outside character class
   */
  public void testOctalNoLeadingZero1() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
    doTest("\\1()\\1\\11[\\1\\11]", """
      OCT_CHAR ('\\1')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      BACKREF ('\\1')
      OCT_CHAR ('\\11')
      CLASS_BEGIN ('[')
      OCT_CHAR ('\\1')
      OCT_CHAR ('\\11')
      CLASS_END (']')""", lexer);
  }

  /**
   * \\1 not valid and \\11 valid under ruby, outside character class
   */
  public void testOctalNoLeadingZero2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MIN_OCTAL_2_DIGITS));
    doTest("\\1()\\1\\11[\\1\\11]", """
      BAD_OCT_VALUE ('\\1')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      BACKREF ('\\1')
      OCT_CHAR ('\\11')
      CLASS_BEGIN ('[')
      OCT_CHAR ('\\1')
      OCT_CHAR ('\\11')
      CLASS_END (']')""", lexer);
  }

  /**
   * \\1 and \\11 not valid under python, outside character class
   */
  public void testOctalNoLeadingZero3() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MIN_OCTAL_3_DIGITS));
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
      CLASS_END (']')""", lexer);
  }


  /** octal is never a back reference inside a character class, valid under js, ruby, python */
  public void testOctalInsideCharClass() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
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
      CLASS_END (']')""", lexer);
  }

  /** \0 always valid under js, ruby, python regex dialects, never a back reference. */
  public void testZeroOctalNoLeadingZero() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
    doTest("\\0()\\0[\\0]", """
      OCT_CHAR ('\\0')
      GROUP_BEGIN ('(')
      GROUP_END (')')
      OCT_CHAR ('\\0')
      CLASS_BEGIN ('[')
      OCT_CHAR ('\\0')
      CLASS_END (']')""", lexer);
  }

  /** three digit octal (\100) always valid, either octal or backreference under js, ruby and python */
  public void testThreeDigitOctalNoLeadingZero() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
    doTest("\\100" +
           "()()()()()()()()()()" +
           "()()()()()()()()()()" +
           "()()()()()()()()()()" +
           "()()()()()()()()()()" +
           "()()()()()()()()()()" +
           "()()()()()()()()()()" +
           "()()()()()()()()()()" +
           "()()()()()()()()()()" +
           "()()()()()()()()()()" +
           "()()()()()()()()()()\\100[\\100]", null, lexer);
  }

  public void testOctalFollowedByDigit() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
    doTest("\\39[\\39]", """
      OCT_CHAR ('\\3')
      CHARACTER ('9')
      CLASS_BEGIN ('[')
      OCT_CHAR ('\\3')
      CHARACTER ('9')
      CLASS_END (']')""", lexer);
  }

  public void testOctalWithLeadingZero() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\0\\123[\\123]", """
      BAD_OCT_VALUE ('\\0')
      BACKREF ('\\1')
      CHARACTER ('2')
      CHARACTER ('3')
      CLASS_BEGIN ('[')
      INVALID_CHARACTER_ESCAPE_TOKEN ('\\1')
      CHARACTER ('2')
      CHARACTER ('3')
      CLASS_END (']')""", lexer);
  }

  public void testOctalWithLeadingZero2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\08\\01\\00\\012\\0123\\0377\\0400", """
      BAD_OCT_VALUE ('\\0')
      CHARACTER ('8')
      OCT_CHAR ('\\01')
      OCT_CHAR ('\\00')
      OCT_CHAR ('\\012')
      OCT_CHAR ('\\0123')
      OCT_CHAR ('\\0377')
      OCT_CHAR ('\\040')
      CHARACTER ('0')""", lexer);
  }

  public void testBackReference() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\105", null, lexer);
  }

  public void testPcreBackReference() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(PCRE_BACK_REFERENCES));
    doTest("(a)\\g105", null, lexer);
  }

  public void testPcreRelativeBackReference() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(PCRE_BACK_REFERENCES));
    doTest("(a)\\g{105}", null, lexer);
  }

  public void testPcreRelativeNegativeBackReference() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(PCRE_BACK_REFERENCES));
    doTest("(a)\\g{-105}", null, lexer);
  }

  public void testPcreRelativeNegativeInvalidBackReference() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(PCRE_BACK_REFERENCES));
    doTest("(a)\\g-105", null, lexer);
  }

  public void testPcreConditionDefine() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(PCRE_CONDITIONS));
    doTest("(?(DEFINE)(?<Name>\\w+))(?P>Name)", null, lexer);
  }

  public void testPcreConditionVersion() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(PCRE_CONDITIONS));
    doTest("(?(VERSION>=10.7)yes|no)", null, lexer);
  }

  public void testNoPcreCondition() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("(?(DEFINE)(?<Name>\\w+))(?P>Name)", null, lexer);
  }

  public void testNoNestedCharacterClasses1() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("[[\\]]", """
      CLASS_BEGIN ('[')
      CHARACTER ('[')
      ESC_CHARACTER ('\\]')
      CLASS_END (']')""", lexer);
  }

  public void testNoNestedCharacterClasses2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
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
      CHARACTER (']')""", lexer);
  }

  public void testNestedCharacterClasses1() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(NESTED_CHARACTER_CLASSES));
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
      CLASS_END (']')""", lexer);
  }

  public void testNestedCharacterClasses2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(NESTED_CHARACTER_CLASSES));
    doTest("[]]", """
      CLASS_BEGIN ('[')
      CHARACTER (']')
      CLASS_END (']')""", lexer);
    doTest("[\\]]", """
      CLASS_BEGIN ('[')
      REDUNDANT_ESCAPE ('\\]')
      CLASS_END (']')""", lexer);
    doTest("[[]]]", """
      CLASS_BEGIN ('[')
      CLASS_BEGIN ('[')
      CHARACTER (']')
      CLASS_END (']')
      CLASS_END (']')""", lexer);
    doTest("[ \\]]", """
      CLASS_BEGIN ('[')
      CHARACTER (' ')
      ESC_CHARACTER ('\\]')
      CLASS_END (']')""", lexer);
    doTest("[\\Q\\E]]", """
      CLASS_BEGIN ('[')
      QUOTE_BEGIN ('\\Q')
      QUOTE_END ('\\E')
      CHARACTER (']')
      CLASS_END (']')""", lexer);
    doTest("[\\Q+\\E]]", """
      CLASS_BEGIN ('[')
      QUOTE_BEGIN ('\\Q')
      CHARACTER ('+')
      QUOTE_END ('\\E')
      CLASS_END (']')
      CHARACTER (']')""", lexer);
    doTest("[^\\Q\\E]]", """
      CLASS_BEGIN ('[')
      CARET ('^')
      QUOTE_BEGIN ('\\Q')
      QUOTE_END ('\\E')
      CHARACTER (']')
      CLASS_END (']')""", lexer);
    doTest("[^\\Q+\\E]]", """
      CLASS_BEGIN ('[')
      CARET ('^')
      QUOTE_BEGIN ('\\Q')
      CHARACTER ('+')
      QUOTE_END ('\\E')
      CLASS_END (']')
      CHARACTER (']')""", lexer);
    final RegExpLexer lexer2 = new RegExpLexer(EnumSet.of(COMMENT_MODE, WHITESPACE_IN_CLASS));
    doTest("[ \t\n]]", """
      CLASS_BEGIN ('[')
      WHITE_SPACE (' ')
      WHITE_SPACE ('\t')
      WHITE_SPACE ('\\n')
      CHARACTER (']')
      CLASS_END (']')""", lexer2);
    doTest("[\\ ]", """
      CLASS_BEGIN ('[')
      ESC_CTRL_CHARACTER ('\\ ')
      CLASS_END (']')""", lexer2);
    doTest("[#comment\nabc]", """
      CLASS_BEGIN ('[')
      COMMENT ('#comment')
      WHITE_SPACE ('\\n')
      CHARACTER ('a')
      CHARACTER ('b')
      CHARACTER ('c')
      CLASS_END (']')""", lexer2);
    doTest("[ ^]]", """
      CLASS_BEGIN ('[')
      WHITE_SPACE (' ')
      CHARACTER ('^')
      CLASS_END (']')
      CHARACTER (']')""", lexer2);
    doTest("[\\", "CLASS_BEGIN ('[')\n" +
                  "INVALID_CHARACTER_ESCAPE_TOKEN ('\\')", lexer2);
    final RegExpLexer lexer3 = new RegExpLexer(EnumSet.of(ALLOW_EMPTY_CHARACTER_CLASS));
    doTest("[]]", """
      CLASS_BEGIN ('[')
      CLASS_END (']')
      CHARACTER (']')""", lexer3);
    doTest("[[]]]", """
      CLASS_BEGIN ('[')
      CHARACTER ('[')
      CLASS_END (']')
      CHARACTER (']')
      CHARACTER (']')""", lexer3);
    doTest("[\\]]", """
      CLASS_BEGIN ('[')
      ESC_CHARACTER ('\\]')
      CLASS_END (']')""", lexer3);
    doTest("[ \\]]", """
      CLASS_BEGIN ('[')
      CHARACTER (' ')
      ESC_CHARACTER ('\\]')
      CLASS_END (']')""", lexer3);
    final RegExpLexer lexer4 = new RegExpLexer(EnumSet.of(COMMENT_MODE));
    doTest("[ ]", """
      CLASS_BEGIN ('[')
      CHARACTER (' ')
      CLASS_END (']')""", lexer4);
    doTest("[#]", """
      CLASS_BEGIN ('[')
      CHARACTER ('#')
      CLASS_END (']')""", lexer4);
    doTest("[\\ ]", """
      CLASS_BEGIN ('[')
      REDUNDANT_ESCAPE ('\\ ')
      CLASS_END (']')""", lexer4);
  }

  public void testBoundaries() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
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
      CLASS_END (']')""", lexer);
  }

  public void testValidEscapes() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\%\\ä", "REDUNDANT_ESCAPE ('\\%')\n" +
                     "REDUNDANT_ESCAPE ('\\ä')", lexer);

    RegExpLexer lexer2 = new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS));
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
      REDUNDANT_ESCAPE ('\\}')""", lexer2);

    RegExpLexer lexer3 = new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS, OMIT_NUMBERS_IN_QUANTIFIERS, OMIT_BOTH_NUMBERS_IN_QUANTIFIERS));
    doTest("{\\}{,\\}{,2\\}", """
      CHARACTER ('{')
      REDUNDANT_ESCAPE ('\\}')
      CHARACTER ('{')
      CHARACTER (',')
      ESC_CHARACTER ('\\}')
      CHARACTER ('{')
      CHARACTER (',')
      CHARACTER ('2')
      ESC_CHARACTER ('\\}')""", lexer3);
  }

  public void testEscapesInsideCharClass() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
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
      CLASS_END (']')""", lexer);

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
      CLASS_END (']')""", lexer);
  }

  public void testUnicode() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(EXTENDED_UNICODE_CHARACTER));
    doTest("\\u{1F680}\\x{1F680}\\u{}\\u{1}\\u{FF}\\x{fff}\\u1234\\u123\\u", """
      UNICODE_CHAR ('\\u{1F680}')
      HEX_CHAR ('\\x{1F680}')
      INVALID_UNICODE_ESCAPE_TOKEN ('\\u{}')
      UNICODE_CHAR ('\\u{1}')
      UNICODE_CHAR ('\\u{FF}')
      HEX_CHAR ('\\x{fff}')
      UNICODE_CHAR ('\\u1234')
      INVALID_UNICODE_ESCAPE_TOKEN ('\\u')
      CHARACTER ('1')
      CHARACTER ('2')
      CHARACTER ('3')
      INVALID_UNICODE_ESCAPE_TOKEN ('\\u')""", lexer);
    final RegExpLexer lexer2 = new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS));
    doTest("\\u{1F680}", """
      INVALID_UNICODE_ESCAPE_TOKEN ('\\u')
      CHARACTER ('{')
      CHARACTER ('1')
      CHARACTER ('F')
      CHARACTER ('6')
      CHARACTER ('8')
      CHARACTER ('0')
      CHARACTER ('}')""", lexer2);
  }

  public void testHexChar() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(ONE_HEX_CHAR_ESCAPE));
    doTest("\\x\\x1\\x01", """
      BAD_HEX_VALUE ('\\x')
      HEX_CHAR ('\\x1')
      HEX_CHAR ('\\x01')""", lexer);
    final RegExpLexer lexer2 = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\x\\x1\\x01", """
      BAD_HEX_VALUE ('\\x')
      BAD_HEX_VALUE ('\\x')
      CHARACTER ('1')
      HEX_CHAR ('\\x01')""", lexer2);
  }

  public void testQuantifier() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS, OMIT_NUMBERS_IN_QUANTIFIERS));
    doTest("a{,10}", """
      CHARACTER ('a')
      LBRACE ('{')
      COMMA (',')
      NUMBER ('10')
      RBRACE ('}')""", lexer);

    doTest("a{10,}", """
      CHARACTER ('a')
      LBRACE ('{')
      NUMBER ('10')
      COMMA (',')
      RBRACE ('}')""", lexer);

    doTest("a{", "CHARACTER ('a')\n" +
                 "CHARACTER ('{')", lexer);

    doTest("a{1", """
      CHARACTER ('a')
      CHARACTER ('{')
      CHARACTER ('1')""", lexer);

    doTest("a{1,", """
      CHARACTER ('a')
      CHARACTER ('{')
      CHARACTER ('1')
      CHARACTER (',')""", lexer);

    doTest("a{,,}", """
      CHARACTER ('a')
      CHARACTER ('{')
      CHARACTER (',')
      CHARACTER (',')
      CHARACTER ('}')""", lexer);

    doTest("[{1,2}]", """
      CLASS_BEGIN ('[')
      CHARACTER ('{')
      CHARACTER ('1')
      CHARACTER (',')
      CHARACTER ('2')
      CHARACTER ('}')
      CLASS_END (']')""", lexer);

    doTest("x\\{9}", """
      CHARACTER ('x')
      ESC_CHARACTER ('\\{')
      CHARACTER ('9')
      CHARACTER ('}')""", lexer);

    doTest("[x\\{9}]", """
      CLASS_BEGIN ('[')
      CHARACTER ('x')
      REDUNDANT_ESCAPE ('\\{')
      CHARACTER ('9')
      CHARACTER ('}')
      CLASS_END (']')""", lexer);

    doTest("x\\{}", """
      CHARACTER ('x')
      REDUNDANT_ESCAPE ('\\{')
      CHARACTER ('}')""", lexer);

    doTest("x{,}", """
      CHARACTER ('x')
      CHARACTER ('{')
      CHARACTER (',')
      CHARACTER ('}')""", lexer);

    doTest("x\\{,}", """
      CHARACTER ('x')
      REDUNDANT_ESCAPE ('\\{')
      CHARACTER (',')
      CHARACTER ('}')""", lexer);
  }

  public void testQuantifier2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS));
    doTest("a{,10}", """
      CHARACTER ('a')
      CHARACTER ('{')
      CHARACTER (',')
      CHARACTER ('1')
      CHARACTER ('0')
      CHARACTER ('}')""", lexer);
  }

  public void testQuantifier3() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(DANGLING_METACHARACTERS, OMIT_NUMBERS_IN_QUANTIFIERS,
                                                         OMIT_BOTH_NUMBERS_IN_QUANTIFIERS));
    doTest("a{,}", """
      CHARACTER ('a')
      LBRACE ('{')
      COMMA (',')
      RBRACE ('}')""", lexer);

    doTest("x\\{,}", """
      CHARACTER ('x')
      ESC_CHARACTER ('\\{')
      CHARACTER (',')
      CHARACTER ('}')""", lexer);
  }

  public void testControlCharacters() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
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
      CLASS_END (']')""", lexer);
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
      CLASS_END (']')""", lexer);
  }

  public void testCaret() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("[\\^\\^]\\^", """
      CLASS_BEGIN ('[')
      ESC_CHARACTER ('\\^')
      REDUNDANT_ESCAPE ('\\^')
      CLASS_END (']')
      ESC_CHARACTER ('\\^')""", lexer);
  }

  public void testPoundSign() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\#(?x)\\#", """
      REDUNDANT_ESCAPE ('\\#')
      SET_OPTIONS ('(?')
      OPTIONS_ON ('x')
      GROUP_END (')')
      ESC_CHARACTER ('\\#')""", lexer);
  }

  public void testNumberedGroupRef() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(PCRE_NUMBERED_GROUP_REF));
    doTest("(abcd)(?1)", null, lexer);
  }

  @Override
  protected Lexer createLexer() {
    return null;
  }

  @Override
  protected String getDirPath() {
    if (new File(getHomePath(), "community/RegExpSupport").isDirectory()) {
      return "/community/RegExpSupport/testData/lexer";
    }
    return "/RegExpSupport/testData/lexer";
  }
}
