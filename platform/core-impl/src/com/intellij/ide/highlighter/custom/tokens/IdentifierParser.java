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

import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;

public class IdentifierParser extends TokenParser {
  private final KeywordParser myKeywordParser;
  private final boolean myIsAdmittingQuote;

  public IdentifierParser(KeywordParser keywordParser) {
    myKeywordParser = keywordParser;
    myIsAdmittingQuote = ContainerUtil.exists(keywordParser.getAllKeywords(), it -> it.contains("'"));
  }

  @Override
  public boolean hasToken(int position) {
    if (!Character.isJavaIdentifierStart(myBuffer.charAt(position))) return false;
    final int start = position;
    for (position++; position < myEndOffset; position++) {
      if (!isIdentifierPart(position)) break;
    }
    IElementType tokenType = CustomHighlighterTokenType.IDENTIFIER;
    myTokenInfo.updateData(start, position, tokenType);
    return true;
  }

  private boolean isIdentifierPart(int position) {
    if (myBuffer.charAt(position) == '-') {
      return !myKeywordParser.hasToken(position, myBuffer, null);
    }
    if (myBuffer.charAt(position) == '\'') {
      return myIsAdmittingQuote;
    }
    return Character.isJavaIdentifierPart(myBuffer.charAt(position));
  }
}
