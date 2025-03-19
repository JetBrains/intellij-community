// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.highlighter.custom;

import com.intellij.ide.highlighter.custom.tokens.TokenInfo;
import com.intellij.ide.highlighter.custom.tokens.TokenParser;
import com.intellij.lexer.LexerBase;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AbstractCustomLexer extends LexerBase {
  protected CharSequence myBuffer = Strings.EMPTY_CHAR_SEQUENCE;
  protected int myStartOffset = 0;
  protected int myEndOffset = 0;
  private final TokenParser[] myTokenParsers;
  protected TokenInfo myCurrentToken;
  protected int myPosition;

  public AbstractCustomLexer(List<? extends TokenParser> tokenParsers) {
    myTokenParsers = tokenParsers.toArray(new TokenParser[0]);
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myStartOffset = startOffset;
    myEndOffset = endOffset;
    myPosition = myStartOffset;
    myCurrentToken = new TokenInfo();
    for (TokenParser tokenParser : myTokenParsers) {
      tokenParser.setBuffer(myBuffer, myStartOffset, myEndOffset);
    }
    advance();
  }

  @Override
  public int getState() {
    return 0;
  }

  @Override
  public IElementType getTokenType() {
    return myCurrentToken.getType();
  }

  @Override
  public int getTokenStart() {
    return myCurrentToken.getStart();
  }

  @Override
  public int getTokenEnd() {
    return myCurrentToken.getEnd();
  }

  @Override
  public void advance() {
    if (myPosition >= myEndOffset) {
      myCurrentToken.updateData(myPosition, myPosition, null);
      return;
    }
    boolean tokenFound = false;
    for (TokenParser tokenParser : myTokenParsers) {
      if (tokenParser.hasToken(myPosition)) {
        tokenParser.getTokenInfo(myCurrentToken);
        if (myCurrentToken.getEnd() <= myCurrentToken.getStart()) {
          throw new AssertionError(tokenParser);
        }
        tokenFound = true;
        break;
      }
    }

    if (!tokenFound) {
      handleTokenNotFound();
    }
    myPosition = myCurrentToken.getEnd();
  }

  protected void handleTokenNotFound() {
    myCurrentToken.updateData(myPosition, myPosition + 1, CustomHighlighterTokenType.CHARACTER);
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myEndOffset;
  }

}
