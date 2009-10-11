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
package com.intellij.psi.impl.source.parsing;

import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;

public class WhiteSpaceAndCommentsProcessor implements TokenProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.WhiteSpaceAndCommentsProcessor");

  public static final TokenProcessor INSTANCE = new WhiteSpaceAndCommentsProcessor();
  private final TokenSet myWhitespaceSet;

  public WhiteSpaceAndCommentsProcessor() {
    this(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
  }

  public WhiteSpaceAndCommentsProcessor(TokenSet whitespaceSet) {
    myWhitespaceSet = whitespaceSet;
  }

  public TreeElement process(Lexer lexer, ParsingContext context) {
    TreeElement first = null;
    TreeElement last = null;
    while (isTokenValid(lexer.getTokenType())) {
      TreeElement tokenElement = createToken(lexer, context);
      IElementType type = lexer.getTokenType();
      if (!myWhitespaceSet.contains(type)) {
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

  protected TreeElement createToken(final Lexer lexer, final ParsingContext context) {
    return ParseUtil.createTokenElement(lexer, context.getCharTable());
  }

  public boolean isTokenValid(IElementType tokenType) {
    return tokenType != null && myWhitespaceSet.contains(tokenType);
  }
}
