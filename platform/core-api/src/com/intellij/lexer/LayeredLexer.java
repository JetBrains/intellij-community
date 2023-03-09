// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class LayeredLexer extends DelegateLexer {
  public static final ThreadLocal<Boolean> ourDisableLayersFlag = new ThreadLocal<>();

  private static final Logger LOG = Logger.getInstance(LayeredLexer.class);
  private static final int IN_LAYER_STATE = 1024; // TODO: Other value?
  private static final int IN_LAYER_LEXER_FINISHED_STATE = 2048;

  private int myState;

  private final Map<IElementType, Lexer> myStartTokenToLayerLexer = new HashMap<>();
  private Lexer myCurrentLayerLexer;
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
        boolean selfStopping = mySelfStoppingLexers.contains(myCurrentLayerLexer);
        myCurrentLayerLexer = null;
        if (!selfStopping) {
          super.advance();
          // In case when we have non-covered gap we should return left part as next token
        } else if (tokenEnd != myBaseTokenEnd) {
          myState = IN_LAYER_LEXER_FINISHED_STATE;
          myLayerLeftPart = tokenEnd;
          if (LOG.isDebugEnabled()) {
            LOG.debug("We've got not covered gap from layered lexer: " + activeLayerLexer +
                      "\n on token: " + getBufferSequence().subSequence(myLayerLeftPart, myBaseTokenEnd));
          }
          return;
        }
        activateLayerIfNecessary();
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
    return ArrayUtil.indexOfIdentity(stopTokens, tokenType) != -1;
  }

  protected boolean isLayerActive() {
    return myCurrentLayerLexer != null;
  }

  private boolean isInLayerEndGap() {
    return myLayerLeftPart != -1;
  }
}