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

import com.intellij.lang.ASTFactory;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.impl.source.ParsingContext;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;

public abstract class DefaultWhiteSpaceTokenProcessorImpl implements TokenProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.parsing.DefaultWhiteSpaceTokenProcessorImpl");

  @Override
  public boolean isTokenValid(IElementType tokenType) {
    return tokenType != null && isInSet(tokenType);
  }

  @Override
  public TreeElement process(Lexer lexer, ParsingContext context) {
    TreeElement first = null;
    TreeElement last = null;
    while (isTokenValid(lexer.getTokenType())) {
      TreeElement tokenElement = ASTFactory.leaf(lexer.getTokenType(), context.tokenText(lexer));
      IElementType type = lexer.getTokenType();

      if (!isInSet(type)) {
        LOG.error("Missed token should be white space or comment:" + tokenElement + "; type:" + type);
        throw new RuntimeException();
      }
      if (last != null) {
        last.rawInsertAfterMe(tokenElement);
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
