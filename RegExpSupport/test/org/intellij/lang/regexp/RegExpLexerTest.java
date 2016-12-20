/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;

import java.util.EnumSet;

import static org.intellij.lang.regexp.RegExpCapability.*;

/**
 * @author Bas Leijdekkers
 */
public class RegExpLexerTest extends LexerTestCase {

  public void testAmpersand() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("[a&&]", "CLASS_BEGIN ('[')\n" +
                    "CHARACTER ('a')\n" +
                    "CHARACTER ('&')\n" +
                    "CHARACTER ('&')\n" +
                    "CLASS_END (']')", lexer);
  }

  public void testQE() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\Q\r\n\\E", "QUOTE_BEGIN ('\\Q')\n" +
                         "CHARACTER ('\n" +
                         "')\n" +
                         "CHARACTER ('\\n')\n" +
                         "QUOTE_END ('\\E')", lexer);
  }

  public void testEditorReplacement() {
    RegExpLexer lexer = new RegExpLexer(EnumSet.of(TRANSFORMATION_ESCAPES));
    final String text = "\\U$1\\E\\u$3\\l$4\\L$2\\E";
    doTest(text, "CHAR_CLASS ('\\U')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('1')\n" +
                 "CHAR_CLASS ('\\E')\n" +
                 "CHAR_CLASS ('\\u')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('3')\n" +
                 "CHAR_CLASS ('\\l')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('4')\n" +
                 "CHAR_CLASS ('\\L')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('2')\n" +
                 "CHAR_CLASS ('\\E')", lexer);

    lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));

    doTest(text, "INVALID_CHARACTER_ESCAPE_TOKEN ('\\U')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('1')\n" +
                 "INVALID_CHARACTER_ESCAPE_TOKEN ('\\E')\n" +
                 "INVALID_UNICODE_ESCAPE_TOKEN ('\\u')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('3')\n" +
                 "INVALID_CHARACTER_ESCAPE_TOKEN ('\\l')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('4')\n" +
                 "INVALID_CHARACTER_ESCAPE_TOKEN ('\\L')\n" +
                 "DOLLAR ('$')\n" +
                 "CHARACTER ('2')\n" +
                 "INVALID_CHARACTER_ESCAPE_TOKEN ('\\E')", lexer);
  }

  public void testIntersection() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(NESTED_CHARACTER_CLASSES));
    doTest("[a&&]", "CLASS_BEGIN ('[')\n" +
                    "CHARACTER ('a')\n" +
                    "ANDAND ('&&')\n" +
                    "CLASS_END (']')", lexer);
  }

  public void testCarets() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("^\\^[^^]", "CARET ('^')\n" +
                       "ESC_CHARACTER ('\\^')\n" +
                       "CLASS_BEGIN ('[')\n" +
                       "CARET ('^')\n" +
                       "CHARACTER ('^')\n" +
                       "CLASS_END (']')", lexer);
  }

  public void testPosixBracketExpression() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(POSIX_BRACKET_EXPRESSIONS));
    doTest("[[:xdigit:]]", "CLASS_BEGIN ('[')\n" +
                           "BRACKET_EXPRESSION_BEGIN ('[:')\n" +
                           "NAME ('xdigit')\n" +
                           "BRACKET_EXPRESSION_END (':]')\n" +
                           "CLASS_END (']')", lexer);
  }

  public void testNegatedPosixBracketExpression() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(POSIX_BRACKET_EXPRESSIONS));
    doTest("[[:^xdigit:]]", "CLASS_BEGIN ('[')\n" +
                            "BRACKET_EXPRESSION_BEGIN ('[:')\n" +
                            "CARET ('^')\n" +
                            "NAME ('xdigit')\n" +
                            "BRACKET_EXPRESSION_END (':]')\n" +
                            "CLASS_END (']')", lexer);
  }

  /**
   * \\177 is the maximum valid octal character under Ruby.
   */
  public void testMaxOctalNoLeadingZero1() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MAX_OCTAL_177));
    doTest("\\177\\200", "OCT_CHAR ('\\177')\n" +
                         "BAD_OCT_VALUE ('\\20')\n" +
                         "CHARACTER ('0')", lexer);
  }

  /**
   * \\377 is the maximum valid octal character under javascript. \\400 is interpreted as \\40 followed by a 0 character.
   * The BAD_OCT_VALUE token is converted to OCT_CHAR in com.intellij.lang.javascript.inject.JSRegexpParserDefinition
   */
  public void testMaxOctalNoLeadingZero2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MAX_OCTAL_377));
    doTest("\\177\\200\\377\\400", "OCT_CHAR ('\\177')\n" +
                                   "OCT_CHAR ('\\200')\n" +
                                   "OCT_CHAR ('\\377')\n" +
                                   "BAD_OCT_VALUE ('\\40')\n" +
                                   "CHARACTER ('0')", lexer);
  }

  /**
   * \\777 is valid octal character in python regex dialect.
   */
  public void testMaxOctalNoLeadingZero3() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
    doTest("\\177\\200\\377\\400\\777", "OCT_CHAR ('\\177')\n" +
                                        "OCT_CHAR ('\\200')\n" +
                                        "OCT_CHAR ('\\377')\n" +
                                        "OCT_CHAR ('\\400')\n" +
                                        "OCT_CHAR ('\\777')", lexer);
  }

  /**
   * \\1 and \\11 valid under js, both inside and outside character class
   */
  public void testOctalNoLeadingZero1() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
    doTest("\\1()\\1\\11[\\1\\11]", "OCT_CHAR ('\\1')\n" +
                                    "GROUP_BEGIN ('(')\n" +
                                    "GROUP_END (')')\n" +
                                    "BACKREF ('\\1')\n" +
                                    "OCT_CHAR ('\\11')\n" +
                                    "CLASS_BEGIN ('[')\n" +
                                    "OCT_CHAR ('\\1')\n" +
                                    "OCT_CHAR ('\\11')\n" +
                                    "CLASS_END (']')", lexer);
  }

  /**
   * \\1 not valid and \\11 valid under ruby, outside character class
   */
  public void testOctalNoLeadingZero2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MIN_OCTAL_2_DIGITS));
    doTest("\\1()\\1\\11[\\1\\11]", "BAD_OCT_VALUE ('\\1')\n" +
                                    "GROUP_BEGIN ('(')\n" +
                                    "GROUP_END (')')\n" +
                                    "BACKREF ('\\1')\n" +
                                    "OCT_CHAR ('\\11')\n" +
                                    "CLASS_BEGIN ('[')\n" +
                                    "OCT_CHAR ('\\1')\n" +
                                    "OCT_CHAR ('\\11')\n" +
                                    "CLASS_END (']')", lexer);
  }

  /**
   * \\1 and \\11 not valid under python, outside character class
   */
  public void testOctalNoLeadingZero3() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO, MIN_OCTAL_3_DIGITS));
    doTest("\\1()\\1\\11\\111[\\1\\11\\111]", "BAD_OCT_VALUE ('\\1')\n" +
                                              "GROUP_BEGIN ('(')\n" +
                                              "GROUP_END (')')\n" +
                                              "BACKREF ('\\1')\n" +
                                              "BAD_OCT_VALUE ('\\11')\n" +
                                              "OCT_CHAR ('\\111')\n" +
                                              "CLASS_BEGIN ('[')\n" +
                                              "OCT_CHAR ('\\1')\n" +
                                              "OCT_CHAR ('\\11')\n" +
                                              "OCT_CHAR ('\\111')\n" +
                                              "CLASS_END (']')", lexer);
  }


  /** octal is never a back reference inside a character class, valid under js, ruby, python */
  public void testOctalInsideCharClass() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
    doTest("()()()()()()()()()()[\\1\\10\\100]", "GROUP_BEGIN ('(')\nGROUP_END (')')\n" +
                                                 "GROUP_BEGIN ('(')\nGROUP_END (')')\n" +
                                                 "GROUP_BEGIN ('(')\nGROUP_END (')')\n" +
                                                 "GROUP_BEGIN ('(')\nGROUP_END (')')\n" +
                                                 "GROUP_BEGIN ('(')\nGROUP_END (')')\n" +
                                                 "GROUP_BEGIN ('(')\nGROUP_END (')')\n" +
                                                 "GROUP_BEGIN ('(')\nGROUP_END (')')\n" +
                                                 "GROUP_BEGIN ('(')\nGROUP_END (')')\n" +
                                                 "GROUP_BEGIN ('(')\nGROUP_END (')')\n" +
                                                 "GROUP_BEGIN ('(')\nGROUP_END (')')\n" +
                                                 "CLASS_BEGIN ('[')\n" +
                                                 "OCT_CHAR ('\\1')\n" +
                                                 "OCT_CHAR ('\\10')\n" +
                                                 "OCT_CHAR ('\\100')\n" +
                                                 "CLASS_END (']')", lexer);
  }

  /** \0 always valid under js, ruby, python regex dialects, never a back reference. */
  public void testZeroOctalNoLeadingZero() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(OCTAL_NO_LEADING_ZERO));
    doTest("\\0()\\0[\\0]", "OCT_CHAR ('\\0')\n" +
                            "GROUP_BEGIN ('(')\n" +
                            "GROUP_END (')')\n" +
                            "OCT_CHAR ('\\0')\n" +
                            "CLASS_BEGIN ('[')\n" +
                            "OCT_CHAR ('\\0')\n" +
                            "CLASS_END (']')", lexer);
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
    doTest("\\39[\\39]", "OCT_CHAR ('\\3')\n" +
                         "CHARACTER ('9')\n" +
                         "CLASS_BEGIN ('[')\n" +
                         "OCT_CHAR ('\\3')\n" +
                         "CHARACTER ('9')\n" +
                         "CLASS_END (']')", lexer);
  }

  public void testOctalWithLeadingZero() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\0\\123[\\123]", "BAD_OCT_VALUE ('\\0')\n" +
                              "BACKREF ('\\1')\n" +
                              "CHARACTER ('2')\n" +
                              "CHARACTER ('3')\n" +
                              "CLASS_BEGIN ('[')\n" +
                              "INVALID_CHARACTER_ESCAPE_TOKEN ('\\1')\n" +
                              "CHARACTER ('2')\n" +
                              "CHARACTER ('3')\n" +
                              "CLASS_END (']')", lexer);
  }

  public void testOctalWithLeadingZero2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("\\08\\01\\00\\012\\0123\\0377\\0400", "BAD_OCT_VALUE ('\\0')\n" +
                                                  "CHARACTER ('8')\n" +
                                                  "OCT_CHAR ('\\01')\n" +
                                                  "OCT_CHAR ('\\00')\n" +
                                                  "OCT_CHAR ('\\012')\n" +
                                                  "OCT_CHAR ('\\0123')\n" +
                                                  "OCT_CHAR ('\\0377')\n" +
                                                  "OCT_CHAR ('\\040')\n" +
                                                  "CHARACTER ('0')", lexer);
  }

  public void testBackReference() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\105", null, lexer);
  }

  public void testNoNestedCharacterClasses1() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("[[\\]]", "CLASS_BEGIN ('[')\n" +
                     "CHARACTER ('[')\n" +
                     "ESC_CHARACTER ('\\]')\n" +
                     "CLASS_END (']')", lexer);
  }

  public void testNoNestedCharacterClasses2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("[a-z&&[^aeuoi]]", "CLASS_BEGIN ('[')\n" +
                              "CHARACTER ('a')\n" +
                              "MINUS ('-')\n" +
                              "CHARACTER ('z')\n" +
                              "CHARACTER ('&')\n" +
                              "CHARACTER ('&')\n" +
                              "CHARACTER ('[')\n" +
                              "CHARACTER ('^')\n" +
                              "CHARACTER ('a')\n" +
                              "CHARACTER ('e')\n" +
                              "CHARACTER ('u')\n" +
                              "CHARACTER ('o')\n" +
                              "CHARACTER ('i')\n" +
                              "CLASS_END (']')\n" +
                              "CHARACTER (']')", lexer);
  }

  public void testNestedCharacterClasses1() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(NESTED_CHARACTER_CLASSES));
    doTest("[a-z&&[^aeuoi]]", "CLASS_BEGIN ('[')\n" +
                              "CHARACTER ('a')\n" +
                              "MINUS ('-')\n" +
                              "CHARACTER ('z')\n" +
                              "ANDAND ('&&')\n" +
                              "CLASS_BEGIN ('[')\n" +
                              "CARET ('^')\n" +
                              "CHARACTER ('a')\n" +
                              "CHARACTER ('e')\n" +
                              "CHARACTER ('u')\n" +
                              "CHARACTER ('o')\n" +
                              "CHARACTER ('i')\n" +
                              "CLASS_END (']')\n" +
                              "CLASS_END (']')", lexer);
  }

  public void testNestedCharacterClasses2() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(NESTED_CHARACTER_CLASSES));
    doTest("[]]", "CLASS_BEGIN ('[')\n" +
                  "CHARACTER (']')\n" +
                  "CLASS_END (']')", lexer);
  }

  @Override
  protected Lexer createLexer() {
    return null;
  }

  @Override
  protected String getDirPath() {
    return "community/RegExpSupport/testData/lexer";
  }
}
