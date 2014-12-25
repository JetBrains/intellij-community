/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

  public void testIntersection() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.NESTED_CHARACTER_CLASSES));
    doTest("[a&&]", "CLASS_BEGIN ('[')\n" +
                    "CHARACTER ('a')\n" +
                    "ANDAND ('&&')\n" +
                    "CLASS_END (']')", lexer);
  }

  public void testPosixBracketExpression() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.POSIX_BRACKET_EXPRESSIONS));
    doTest("[[:xdigit:]]", "CLASS_BEGIN ('[')\n" +
                           "BRACKET_EXPRESSION_BEGIN ('[:')\n" +
                           "NAME ('xdigit')\n" +
                           "BRACKET_EXPRESSION_END (':]')\n" +
                           "CLASS_END (']')", lexer);
  }

  @Override
  protected Lexer createLexer() {
    return null;
  }

  @Override
  protected String getDirPath() {
    return "";
  }
}
