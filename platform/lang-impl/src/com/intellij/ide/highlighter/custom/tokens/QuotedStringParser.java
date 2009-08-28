package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;

/**
 * @author dsl
 */
public class QuotedStringParser extends PrefixedTokenParser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.highlighter.custom.tokens.QuotedStringParser");
  private final char myQuote;
  private final boolean myAllowEscapes;

  public QuotedStringParser(String quote, IElementType type, boolean allowEscapes) {
    super(quote, type);
    LOG.assertTrue(quote.length() == 1);
    myQuote = quote.charAt(0);
    myAllowEscapes = allowEscapes;
  }

  protected int getTokenEnd(int position) {
    boolean escaped = false;
    for(; position < myEndOffset; position++) {
      final char c = myBuffer.charAt(position);
      final boolean escapedStatus = escaped;

      if (myAllowEscapes && c == '\\') {
        escaped = !escaped;
      }

      if(!escaped && c == myQuote) return position + 1;
      if(c == '\n') return position;
      if (escapedStatus && escaped) {
        escaped = false;
      }
    }
    return position;
  }
}
