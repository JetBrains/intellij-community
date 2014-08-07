/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lexer;

import com.intellij.util.CharTable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class LexerUtil {
  private LexerUtil() {}

  public static CharSequence getTokenText(Lexer lexer) {
    return lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd());
  }

  public static CharSequence internToken(Lexer lexer, CharTable table) {
    return table.intern(getTokenText(lexer));
  }

  @Contract("!null->!null")
  public static Lexer getRootLexer(@Nullable Lexer lexer) {
    while (lexer instanceof DelegateLexer) {
      lexer = ((DelegateLexer)lexer).getDelegate();
    }
    return lexer;
  }
}
