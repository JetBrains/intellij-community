package com.intellij.psi.impl.source;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerUtil;
import com.intellij.util.CharTable;

/**
 * @author ven
 */
public class ParsingContext {
  private final CharTable myTable;
  
  public ParsingContext(final CharTable table) {
    myTable = table;
  }

  public CharTable getCharTable() {
    return myTable;
  }

  public CharSequence tokenText(Lexer lexer) {
    return LexerUtil.internToken(lexer, myTable);
  }
}
