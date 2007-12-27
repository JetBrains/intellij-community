package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;

/**
 * @author dsl
 */
public class IdentifierParser extends BaseTokenParser {
  public IdentifierParser() {
  }

  public boolean hasToken(int position) {
    if (!Character.isJavaIdentifierStart(myBuffer.charAt(position))) return false;
    final int start = position;
    for (position++; position < myEndOffset; position++) {
      final char c = myBuffer.charAt(position);
      if (!isIdentifierPart(c)) break;
    }
    IElementType tokenType = CustomHighlighterTokenType.IDENTIFIER;
    myTokenInfo.updateData(start, position, tokenType);
    return true;
  }

  private boolean isIdentifierPart(final char c) {
    return Character.isJavaIdentifierPart(c) || c == '-';
  }

  public int getSmartUpdateShift() {
    return 0;
  }
}
