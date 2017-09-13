/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.lang.java.lexer;

import com.intellij.lexer.DocCommentTokenTypes;
import com.intellij.lexer.JavaDocTokenTypes;
import com.intellij.lexer.LexerBase;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaDocLexer extends MergingLexerAdapter {
  public JavaDocLexer(@NotNull LanguageLevel level) {
    this(JavaDocTokenTypes.INSTANCE, level.isAtLeast(LanguageLevel.JDK_1_5));
  }

  private JavaDocLexer(DocCommentTokenTypes tokenTypes, boolean isJdk15Enabled) {
    super(new AsteriskStripperLexer(new _JavaDocLexer(isJdk15Enabled, tokenTypes), tokenTypes), tokenTypes.spaceCommentsTokenSet());
  }

  private static class AsteriskStripperLexer extends LexerBase {
    private final _JavaDocLexer myFlex;
    private final DocCommentTokenTypes myTokenTypes;
    private CharSequence myBuffer;
    private int myBufferIndex;
    private int myBufferEndOffset;
    private int myTokenEndOffset;
    private int myState;
    private IElementType myTokenType;
    private boolean myAfterLineBreak;
    private boolean myInLeadingSpace;

    public AsteriskStripperLexer(final _JavaDocLexer flex, final DocCommentTokenTypes tokenTypes) {
      myFlex = flex;
      myTokenTypes = tokenTypes;
    }

    @Override
    public final void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer;
      myBufferIndex = startOffset;
      myBufferEndOffset = endOffset;
      myTokenType = null;
      myTokenEndOffset = startOffset;
      myFlex.reset(myBuffer, startOffset, endOffset, initialState);
    }

    @Override
    public int getState() {
      return myState;
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    @Override
    public int getBufferEnd() {
      return myBufferEndOffset;
    }

    @Override
    public final IElementType getTokenType() {
      locateToken();
      return myTokenType;
    }

    @Override
    public final int getTokenStart() {
      locateToken();
      return myBufferIndex;
    }

    @Override
    public final int getTokenEnd() {
      locateToken();
      return myTokenEndOffset;
    }

    @Override
    public final void advance() {
      locateToken();
      myTokenType = null;
    }

    protected final void locateToken() {
      if (myTokenType != null) return;
      _locateToken();

      if (myTokenType == myTokenTypes.space()) {
        myAfterLineBreak = CharArrayUtil.containLineBreaks(myBuffer, getTokenStart(), getTokenEnd());
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
        while (myTokenEndOffset < myBufferEndOffset && myBuffer.charAt(myTokenEndOffset) == '*' &&
               (myTokenEndOffset + 1 >= myBufferEndOffset || myBuffer.charAt(myTokenEndOffset + 1) != '/')) {
          myTokenEndOffset++;
        }

        myInLeadingSpace = true;
        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = myTokenTypes.commentLeadingAsterisks();
          return;
        }
      }

      if (myInLeadingSpace) {
        myInLeadingSpace = false;
        boolean lf = false;
        while (myTokenEndOffset < myBufferEndOffset && Character.isWhitespace(myBuffer.charAt(myTokenEndOffset))) {
          if (myBuffer.charAt(myTokenEndOffset) == '\n') lf = true;
          myTokenEndOffset++;
        }

        final int state = myFlex.yystate();
        if (state == _JavaDocLexer.COMMENT_DATA ||
            myTokenEndOffset < myBufferEndOffset && (myBuffer.charAt(myTokenEndOffset) == '@' ||
                                                     myBuffer.charAt(myTokenEndOffset) == '{' ||
                                                     myBuffer.charAt(myTokenEndOffset) == '\"' ||
                                                     myBuffer.charAt(myTokenEndOffset) == '<')) {
          myFlex.yybegin(_JavaDocLexer.COMMENT_DATA_START);
        }

        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = lf ||
                        state == _JavaDocLexer.PARAM_TAG_SPACE || state == _JavaDocLexer.TAG_DOC_SPACE ||
                        state == _JavaDocLexer.INLINE_TAG_NAME || state == _JavaDocLexer.DOC_TAG_VALUE_IN_PAREN
                        ? myTokenTypes.space() : myTokenTypes.commentData();

          return;
        }
      }

      flexLocateToken();
    }

    private void flexLocateToken() {
      //noinspection Duplicates
      try {
        myState = myFlex.yystate();
        myFlex.goTo(myBufferIndex);
        myTokenType = myFlex.advance();
        myTokenEndOffset = myFlex.getTokenEnd();
      }
      catch (IOException e) {
        // Can't be
      }
    }
  }
}