/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.highlighting;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.BracePair;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public class JavaPairedBraceMatcher extends PairedBraceMatcherAdapter {
  private static class Holder {
    private static final TokenSet TYPE_TOKENS =
      TokenSet.orSet(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET,
                     TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.COMMA,
                                     JavaTokenType.AT,//anno
                                     JavaTokenType.RBRACKET, JavaTokenType.LBRACKET, //arrays
                                     JavaTokenType.QUEST, JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.SUPER_KEYWORD));//wildcards
  }

  public JavaPairedBraceMatcher() {
    super(new JavaBraceMatcher(), JavaLanguage.INSTANCE);
  }

  @Override
  public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    return isBrace(iterator, fileText, fileType, true);
  }

  @Override
  public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    return isBrace(iterator, fileText, fileType, false);
  }

  private boolean isBrace(HighlighterIterator iterator,
                          CharSequence fileText,
                          FileType fileType,
                          boolean left) {
    final BracePair pair = findPair(left, iterator, fileText, fileType);
    if (pair == null) return false;

    final IElementType opposite = left ? JavaTokenType.GT : JavaTokenType.LT;
    if ((left ? pair.getRightBraceType() : pair.getLeftBraceType()) != opposite) return true;

    if (fileType != JavaFileType.INSTANCE) return false;

    final IElementType braceElementType = left ? JavaTokenType.LT : JavaTokenType.GT;
    int count = 0;
    try {
      int paired = 1;
      while (true) {
        count++;
        if (left) {
          iterator.advance();
        } else {
          iterator.retreat();
        }
        if (iterator.atEnd()) break;
        final IElementType tokenType = iterator.getTokenType();
        if (tokenType == opposite) {
          paired--;
          if (paired == 0) return true;
          continue;
        }

        if (tokenType == braceElementType) {
          paired++;
          continue;
        }

        if (!Holder.TYPE_TOKENS.contains(tokenType)) {
          return false;
        }
      }
      return false;
    }
    finally {
      while (count-- > 0) {
        if (left) {
          iterator.retreat();
        } else {
          iterator.advance();
        }
      }
    }
  }
}

