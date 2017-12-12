/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;

/**
 * @author max
 */
public class LayeredLexer extends DelegateLexer {
  public static ThreadLocal<Boolean> ourDisableLayersFlag = new ThreadLocal<>();

  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.LayeredLexer");
  private static final int IN_LAYER_STATE = 1024; // TODO: Other value?
  private static final int IN_LAYER_LEXER_FINISHED_STATE = 2048;

  private int myState;

  private final Map<IElementType, Lexer> myStartTokenToLayerLexer = new HashMap<>();
  private Lexer myCurrentLayerLexer;
  // In some cases IDEA-57933 layered lexer is not able to parse all the token, that triggered this lexer,
  // for this purposes we store left part of token in the following fields
  private IElementType myCurrentBaseTokenType;
  private int myLayerLeftPart = -1;
  private int myBaseTokenEnd = -1;

  private final HashSet<Lexer> mySelfStoppingLexers = new HashSet<>(1);
  private final HashMap<Lexer, IElementType[]> myStopTokens = new HashMap<>(1);


  public LayeredLexer(Lexer baseLexer) {
    super(baseLexer);
  }

  public void registerSelfStoppingLayer(Lexer lexer, IElementType[] startTokens, IElementType[] stopTokens) {
    if (Boolean.TRUE.equals(ourDisableLayersFlag.get())) return;
    registerLayer(lexer, startTokens);
    mySelfStoppingLexers.add(lexer);
    myStopTokens.put(lexer, stopTokens);
  }

  public void registerLayer(Lexer lexer, IElementType... startTokens) {
    if (Boolean.TRUE.equals(ourDisableLayersFlag.get())) return;
    for (IElementType startToken : startTokens) {
      LOG.assertTrue(!myStartTokenToLayerLexer.containsKey(startToken));
      myStartTokenToLayerLexer.put(startToken, lexer);
    }
  }

  private void activateLayerIfNecessary() {
    final IElementType baseTokenType = super.getTokenType();
    myCurrentLayerLexer = findLayerLexer(baseTokenType);
    if (myCurrentLayerLexer != null) {
      myCurrentBaseTokenType = baseTokenType;
      myBaseTokenEnd = super.getTokenEnd();
      myCurrentLayerLexer.start(super.getBufferSequence(), super.getTokenStart(), super.getTokenEnd());
      if (mySelfStoppingLexers.contains(myCurrentLayerLexer)) {
        super.advance();
      }
    }
  }

  @Nullable
  protected Lexer findLayerLexer(IElementType baseTokenType) {
    return myStartTokenToLayerLexer.get(baseTokenType);
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    LOG.assertTrue(initialState != IN_LAYER_STATE, "Restoring to layer is not supported.");
    myState = initialState;
    myCurrentLayerLexer = null;

    super.start(buffer, startOffset, endOffset, initialState);
    activateLayerIfNecessary();
  }

  @Override
  public int getState() {
    return myState;
  }

  @Override
  public IElementType getTokenType() {
    if (isInLayerEndGap()) {
      return myCurrentBaseTokenType;
    }
    return isLayerActive() ? myCurrentLayerLexer.getTokenType() : super.getTokenType();
  }

  @Override
  public int getTokenStart() {
    if (isInLayerEndGap()) {
      return myLayerLeftPart;
    }
    return isLayerActive() ? myCurrentLayerLexer.getTokenStart() : super.getTokenStart();
  }

  @Override
  public int getTokenEnd() {
    if (isInLayerEndGap()) {
      return myBaseTokenEnd;
    }
    return isLayerActive() ? myCurrentLayerLexer.getTokenEnd() : super.getTokenEnd();
  }

  @Override
  public void advance() {
    if (isInLayerEndGap()){
      myLayerLeftPart = -1;
      myState = super.getState();
      return;
    }

    if (isLayerActive()) {
      final Lexer activeLayerLexer = myCurrentLayerLexer;
      IElementType layerTokenType = activeLayerLexer.getTokenType();
      if (!isStopToken(myCurrentLayerLexer, layerTokenType)) {
        myCurrentLayerLexer.advance();
        layerTokenType = myCurrentLayerLexer.getTokenType();
      } else {
        layerTokenType = null;
      }
      if (layerTokenType == null) {
        int tokenEnd = myCurrentLayerLexer.getTokenEnd();
        if (!mySelfStoppingLexers.contains(myCurrentLayerLexer)) {
          myCurrentLayerLexer = null;
          super.advance();
          activateLayerIfNecessary();
        } else {
          myCurrentLayerLexer = null;

          // In case when we have non-covered gap we should return left part as next token
          if (tokenEnd != myBaseTokenEnd) {
            myState = IN_LAYER_LEXER_FINISHED_STATE;
            myLayerLeftPart = tokenEnd;
            if (LOG.isDebugEnabled()) {
              LOG.debug("We've got not covered gap from layered lexer: " + activeLayerLexer +
                        "\n on token: " + getBufferSequence().subSequence(myLayerLeftPart, myBaseTokenEnd));
            }
            return;
          }
        }
      }
    } else {
      super.advance();
      activateLayerIfNecessary();
    }
    myState = isLayerActive() ? IN_LAYER_STATE : super.getState();
  }

  @NotNull
  @Override
  public LexerPosition getCurrentPosition() {
    return new LexerPositionImpl(getTokenStart(), getState());
  }

  @Override
  public void restore(@NotNull LexerPosition position) {
    start(getBufferSequence(), position.getOffset(), getBufferEnd(), position.getState());
  }

  private boolean isStopToken(Lexer lexer, IElementType tokenType) {
    final IElementType[] stopTokens = myStopTokens.get(lexer);
    if (stopTokens == null) return false;
    for (IElementType stopToken : stopTokens) {
      if (stopToken == tokenType) return true;
    }
    return false;
  }

  protected boolean isLayerActive() {
    return myCurrentLayerLexer != null;
  }

  private boolean isInLayerEndGap() {
    return myLayerLeftPart != -1;
  }
}