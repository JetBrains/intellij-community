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
package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;

import java.util.HashSet;
import java.util.Map;

/**
 * @author max
 */
public class LayeredLexer extends DelegateLexer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.LayeredLexer");
  private static final int IN_LAYER_STATE = 1024; // TODO: Other value?
  private static final int IN_LAYER_LEXER_FINISHED_STATE = 2048;

  private int myState;

  private final Map<IElementType, Lexer> myStartTokenToLayerLexer = new HashMap<IElementType, Lexer>();
  private Lexer myCurrentLayerLexer;
  // In some cases IDEA-57933 layered lexer is not able to parse all the token, that triggered this lexer,
  // for this purposes we store left part of token in the following fields
  private IElementType myCurrentBaseTokenType;
  private int myLayerLeftPart = -1;
  private int myBaseTokenEnd = -1;

  private final HashSet<Lexer> mySelfStoppingLexers = new HashSet<Lexer>(1);
  private final HashMap<Lexer, IElementType[]> myStopTokens = new HashMap<Lexer,IElementType[]>(1);


  public LayeredLexer(Lexer baseLexer) {
    super(baseLexer);
  }

  public void registerSelfStoppingLayer(Lexer lexer, IElementType[] startTokens, IElementType[] stopTokens) {
    registerLayer(lexer, startTokens);
    mySelfStoppingLexers.add(lexer);
    myStopTokens.put(lexer, stopTokens);
  }

  public void registerLayer(Lexer lexer, IElementType... startTokens) {
    for (IElementType startToken : startTokens) {
      LOG.assertTrue(!myStartTokenToLayerLexer.containsKey(startToken));
      myStartTokenToLayerLexer.put(startToken, lexer);
    }
  }

  private void activateLayerIfNecessary() {
    final IElementType baseTokenType = super.getTokenType();
    myCurrentLayerLexer = myStartTokenToLayerLexer.get(baseTokenType);
    if (myCurrentLayerLexer != null) {
      myCurrentBaseTokenType = baseTokenType;
      myBaseTokenEnd = super.getTokenEnd();
      myCurrentLayerLexer.start(super.getBufferSequence(), super.getTokenStart(), super.getTokenEnd());
      if (mySelfStoppingLexers.contains(myCurrentLayerLexer)) {
        super.advance();
      }
    }
  }
  
  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    LOG.assertTrue(initialState != IN_LAYER_STATE, "Restoring to layer is not supported.");
    myState = initialState;
    myCurrentLayerLexer = null;

    super.start(buffer, startOffset, endOffset, initialState);
    activateLayerIfNecessary();
  }

  public int getState() {
    return myState;
  }

  public IElementType getTokenType() {
    if (myState == IN_LAYER_LEXER_FINISHED_STATE) {
      return myCurrentBaseTokenType;
    }
    return isLayerActive() ? myCurrentLayerLexer.getTokenType() : super.getTokenType();
  }

  public int getTokenStart() {
    if (myState == IN_LAYER_LEXER_FINISHED_STATE) {
      return myLayerLeftPart;
    }
    return isLayerActive() ? myCurrentLayerLexer.getTokenStart() : super.getTokenStart();
  }

  public int getTokenEnd() {
    if (myState == IN_LAYER_LEXER_FINISHED_STATE) {
      return myBaseTokenEnd;
    }
    return isLayerActive() ? myCurrentLayerLexer.getTokenEnd() : super.getTokenEnd();
  }

  public void advance() {
    if (myState == IN_LAYER_LEXER_FINISHED_STATE){
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
            if (LOG.isDebugEnabled()) {
              LOG.debug("We've got not covered gap from layered lexer: " + activeLayerLexer +
                        "\n on token: " + getBufferSequence().subSequence(myLayerLeftPart, myBaseTokenEnd));
            }
            myState = IN_LAYER_LEXER_FINISHED_STATE;
            myLayerLeftPart = tokenEnd;
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

  public LexerPosition getCurrentPosition() {
    return new LexerPositionImpl(getTokenStart(), getState());
  }

  public void restore(LexerPosition position) {
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

  private boolean isLayerActive() {
    return myCurrentLayerLexer != null;
  }
}
