/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

  @Override
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
