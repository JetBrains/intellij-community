// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.lexer;

import com.intellij.lexer.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class JavaDocLexer extends MergingLexerAdapter {
  public JavaDocLexer(@NotNull LanguageLevel level) {
    this(JavaDocTokenTypes.INSTANCE, level.isAtLeast(LanguageLevel.JDK_1_5));
  }

  private JavaDocLexer(JavaDocCommentTokenTypes tokenTypes, boolean isJdk15Enabled) {
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
    private boolean myMarkdownMode;

    AsteriskStripperLexer(final _JavaDocLexer flex, final DocCommentTokenTypes tokenTypes) {
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
      myAfterLineBreak = false;
      myInLeadingSpace = false;
      myFlex.reset(myBuffer, startOffset, endOffset, initialState);
      selectLexerMode(buffer, startOffset);
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

    /** Enables markdown mode if necessary */
    private void selectLexerMode(@NotNull CharSequence buffer, int startOffset) {
      myMarkdownMode = StringUtil.startsWith(buffer, startOffset, "///");
      myFlex.setMarkdownMode(myMarkdownMode);
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

        if (myMarkdownMode) {
          while (detectLeadingSlashes()) {
            myTokenEndOffset += 3;
          }
        }else {
          while (detectLeadingAsterisks()) {
            myTokenEndOffset++;
          }
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
            state != _JavaDocLexer.SNIPPET_TAG_BODY_DATA &&
            myTokenEndOffset < myBufferEndOffset && (myBuffer.charAt(myTokenEndOffset) == '@' ||
                                                     myBuffer.charAt(myTokenEndOffset) == '{' ||
                                                     myBuffer.charAt(myTokenEndOffset) == '\"' ||
                                                     myBuffer.charAt(myTokenEndOffset) == '<')) {
          myFlex.yybegin(_JavaDocLexer.COMMENT_DATA_START);
        }

        if (myBufferIndex < myTokenEndOffset) {
          myTokenType = lf ||
                        state == _JavaDocLexer.PARAM_TAG_SPACE || state == _JavaDocLexer.TAG_DOC_SPACE ||
                        state == _JavaDocLexer.INLINE_TAG_NAME || state == _JavaDocLexer.DOC_TAG_VALUE_IN_PAREN ||
                        state == _JavaDocLexer.SNIPPET_TAG_COMMENT_DATA_UNTIL_COLON
                        ? myTokenTypes.space() : myTokenTypes.commentData();

          return;
        }
      }

      flexLocateToken();
    }

    /** @return true for * or *\/ */
    private boolean detectLeadingAsterisks() {
      return myTokenEndOffset < myBufferEndOffset && myBuffer.charAt(myTokenEndOffset) == '*' &&
      (myTokenEndOffset + 1 >= myBufferEndOffset || myBuffer.charAt(myTokenEndOffset + 1) != '/');
    }

    /** @return The current token is the start of three leading slashes */
    private boolean detectLeadingSlashes() {
      return myTokenEndOffset + 2 < myBufferEndOffset && StringUtil.startsWith(myBuffer, myTokenEndOffset, "///");
    }

    private void flexLocateToken() {
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