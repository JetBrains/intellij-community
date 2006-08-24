/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.lexer;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;

import java.io.IOException;

public class JavaDocLexer extends MergingLexerAdapter {
  private static final TokenSet TOKENS_TO_MERGE = TokenSet.create(
    JavaDocTokenType.DOC_COMMENT_DATA,
    JavaDocTokenType.DOC_SPACE
  );

  public JavaDocLexer(final boolean isJdk15Enabled) {
    super(new AsteriskStripperLexer(new _JavaDocLexer(isJdk15Enabled)), TOKENS_TO_MERGE);
  }

  private static class AsteriskStripperLexer extends LexerBase {
    private _JavaDocLexer myFlex;
    private char[] myBuffer;
    private int myBufferStart;
    private int myBufferIndex;
    private int myBufferEndOffset;
    private int myTokenEndOffset;
    private int myState;
    private IElementType myTokenType;
    private boolean myAfterLineBreak;
    private boolean myInLeadingSpace;

    public AsteriskStripperLexer(final _JavaDocLexer flex) {
      myFlex = flex;
    }

    public final void start(char[] buffer) {
      start(buffer, 0, buffer.length);
    }

    public final void start(char[] buffer, int startOffset, int endOffset) {
      myBuffer = buffer;
      myBufferIndex = myBufferStart = startOffset;
      myBufferEndOffset = endOffset;
      myTokenType = null;
      myTokenEndOffset = startOffset;
      myFlex.reset(new CharArrayCharSequence(myBuffer, startOffset, endOffset), 0);
    }

    public final void start(char[] buffer, int startOffset, int endOffset, int initialState) {
      start(buffer, startOffset, endOffset);
    }

    public int getState() {
      return myState;
    }


    public char[] getBuffer() {
      return myBuffer;
    }

    public int getBufferEnd() {
      return myBufferEndOffset;
    }

    public final IElementType getTokenType() {
      locateToken();
      return myTokenType;
    }

    public final int getTokenStart() {
      locateToken();
      return myBufferIndex;
    }

    public final int getTokenEnd() {
      locateToken();
      return myTokenEndOffset;
    }


    public final void advance() {
      locateToken();
      myTokenType = null;
    }

    protected final void locateToken() {
      if (myTokenType != null) return;
      _locateToken();

      if (myTokenType == JavaDocTokenType.DOC_SPACE) {
        myAfterLineBreak = CharArrayUtil.containLineBreaks(new CharArrayCharSequence(myBuffer, getTokenStart(), getTokenEnd()));
      }
    }

    private void _locateToken() {
      if (myTokenEndOffset == myBufferEndOffset) {
        myTokenType = null;
        myBufferIndex = myBufferEndOffset;
        return;
      }

      myBufferIndex = myTokenEndOffset;

      if (myAfterLineBreak) {
        myAfterLineBreak = false;
        while (myTokenEndOffset < myBufferEndOffset && myBuffer[myTokenEndOffset] == '*' &&
               (myTokenEndOffset + 1 >= myBufferEndOffset || myBuffer[myTokenEndOffset + 1] != '/')) {
          myTokenEndOffset++;
        }

        myInLeadingSpace = true;
        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS;
          return;
        }
      }

      if (myInLeadingSpace) {
        myInLeadingSpace = false;
        boolean lf = false;
        while (myTokenEndOffset < myBufferEndOffset && Character.isWhitespace(myBuffer[myTokenEndOffset])) {
          if (myBuffer[myTokenEndOffset] == '\n') lf = true;
          myTokenEndOffset++;
        }

        final int state = myFlex.yystate();
        if (state == _JavaDocLexer.COMMENT_DATA) {
          myFlex.yybegin(_JavaDocLexer.COMMENT_DATA_START);
        }

        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = lf || state == _JavaDocLexer.PARAM_TAG_SPACE || state == _JavaDocLexer.TAG_DOC_SPACE || state == _JavaDocLexer.INLINE_TAG_NAME || state == _JavaDocLexer.DOC_TAG_VALUE_IN_PAREN
                        ? JavaDocTokenType.DOC_SPACE
                        : JavaDocTokenType.DOC_COMMENT_DATA;
          
          return;
        }
      }
      
      flexLocateToken();
    }

    private void flexLocateToken() {
      try {
        myState = myFlex.yystate();
        myFlex.goTo(myBufferIndex - myBufferStart);
        myTokenType = myFlex.advance();
        myTokenEndOffset = myFlex.getTokenEnd() + myBufferStart;
      }
      catch (IOException e) {
        // Can't be
      }
    }
  }
}