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
package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class FlexAdapter extends LexerBase {

  private static final Logger LOG = Logger.getInstance(FlexAdapter.class);
  private static final boolean logLexerErrors = SystemProperties.getBooleanProperty("log.flex.adapter.errors", true); // Used by Upsource

  private final FlexLexer myFlex;

  private IElementType myTokenType;
  private CharSequence myText;

  private int myTokenStart;
  private int myTokenEnd;

  private int myBufferEnd;
  private int myState;

  public FlexAdapter(@NotNull FlexLexer flex) {
    myFlex = flex;
  }

  public FlexLexer getFlex() {
    return myFlex;
  }

  @Override
  public void start(@NotNull final CharSequence buffer, int startOffset, int endOffset, final int initialState) {
    myText = buffer;
    myTokenStart = myTokenEnd = startOffset;
    myBufferEnd = endOffset;
    myFlex.reset(myText, startOffset, endOffset, initialState);    
    myTokenType = null;
  }

  @Override
  public int getState() {
    locateToken();
    return myState;
  }

  @Override
  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  @Override
  public int getTokenStart() {
    locateToken();
    return myTokenStart;
  }

  @Override
  public int getTokenEnd() {
    locateToken();
    return myTokenEnd;
  }

  @Override
  public void advance() {
    locateToken();
    myTokenType = null;
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return myText;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEnd;
  }

  protected void locateToken() {
    if (myTokenType != null) return;

    try {
      myTokenStart = myFlex.getTokenEnd();
      myState = myFlex.yystate();
      myTokenType = myFlex.advance();
      myTokenEnd = myFlex.getTokenEnd();
    }
    catch (Exception  e) {
      if (logLexerErrors) {
        LOG.error(myFlex.getClass().getName(), e);
      }
      myTokenType = TokenType.WHITE_SPACE;
      myTokenEnd = myBufferEnd;
    }
    catch (Error e) {
      if (logLexerErrors) {
        LOG.error(myFlex.getClass().getName(), e);
      }
      myTokenType = TokenType.WHITE_SPACE;
      myTokenEnd = myBufferEnd;
    }
  }
}
