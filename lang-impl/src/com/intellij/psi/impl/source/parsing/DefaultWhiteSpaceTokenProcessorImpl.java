package com.intellij.psi.impl.source.parsing;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.lang.ASTFactory;

public abstract class DefaultWhiteSpaceTokenProcessorImpl implements TokenProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.DefaultWhiteSpaceTokenProcessorImpl");

  public boolean isTokenValid(IElementType tokenType) {
    return tokenType != null && isInSet(tokenType);
  }

  public TreeElement process(Lexer lexer, ParsingContext context) {
    TreeElement first = null;
    TreeElement last = null;
    while (isTokenValid(lexer.getTokenType())) {
      TreeElement tokenElement = ASTFactory.leaf(lexer.getTokenType(), lexer.getBufferSequence(), lexer.getTokenStart(), lexer.getTokenEnd(),
                                                 context.getCharTable());
      IElementType type = lexer.getTokenType();

      if (!isInSet(type)) {
        LOG.error("Missed token should be white space or comment:" + tokenElement);
        throw new RuntimeException();
      }
      if (last != null) {
        last.setTreeNext(tokenElement);
        tokenElement.setTreePrev(last);
        last = tokenElement;
      }
      else {
        first = last = tokenElement;
      }
      lexer.advance();
    }
    return first;
  }

  protected abstract boolean isInSet(final IElementType type);
}
