package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.CustomHighlighterTokenType;

/**
 * @author dsl
 */
public class QuotedStringParser extends PrefixedTokenParser {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.highlighter.custom.tokens.QuotedStringParser");
  private char myQuote;

  public QuotedStringParser(String quote) {
    super(quote, CustomHighlighterTokenType.STRING);
    LOG.assertTrue(quote.length() == 1);
    myQuote = quote.charAt(0);
  }

  protected int getTokenEnd(int position) {
    for(; position < myEndOffset; position++) {
      if(myBuffer.charAt(position) == myQuote) return position + 1;
      if(myBuffer.charAt(position) == '\n') return position;
    }
    return position;
  }
}
