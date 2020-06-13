// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.util.CharTable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public final class LexerUtil {
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
