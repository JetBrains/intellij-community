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
import junit.framework.TestCase;

import java.util.EnumSet;

/**
 * @author Bas Leijdekkers
 */
public class RegExpLexerTest extends TestCase {

  public void testAmpersand() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.noneOf(RegExpCapability.class));
    doTest("[a&&]", lexer, "CLASS_BEGIN([) CHARACTER(a) CHARACTER(&) CHARACTER(&) CLASS_END(])");
  }

  public void testIntersection() {
    final RegExpLexer lexer = new RegExpLexer(EnumSet.of(RegExpCapability.NESTED_CHARACTER_CLASSES));
    doTest("[a&&]", lexer, "CLASS_BEGIN([) CHARACTER(a) ANDAND(&&) CLASS_END(])");
  }

  public static void doTest(String text, Lexer lexer, String expectedTokens) {
    lexer.start(text);
    final StringBuilder actualTokens = new StringBuilder();
    boolean first = true;
    while (lexer.getTokenType() != null) {
      if (first) first = false;
      else actualTokens.append(" ");
      actualTokens.append(lexer.getTokenType()).append('(').append(lexer.getTokenText()).append(')');
      lexer.advance();
    }
    assertEquals(expectedTokens, actualTokens.toString());
  }
}
