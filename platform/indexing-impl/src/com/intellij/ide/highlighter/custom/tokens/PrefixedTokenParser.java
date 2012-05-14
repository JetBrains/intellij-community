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

package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.tree.IElementType;

/**
 * @author dsl
 */
public abstract class PrefixedTokenParser extends TokenParser {
  private final char[] myPrefix;
  private final IElementType myTokenType;

  public PrefixedTokenParser(String prefix, IElementType tokenType) {
    myTokenType = tokenType;
    myPrefix = prefix.toCharArray();
  }

  public boolean hasToken(int position) {
    final int start = position;
    int i;
    for (i = 0; i < myPrefix.length && position < myEndOffset; i++, position++) {
      if (myPrefix[i] != myBuffer.charAt(position)) break;
    }
    if (i < myPrefix.length) return false;
    int end = getTokenEnd(position);
    myTokenInfo.updateData(start, end, myTokenType);
    return true;
  }

  protected abstract int getTokenEnd(int position);
}
