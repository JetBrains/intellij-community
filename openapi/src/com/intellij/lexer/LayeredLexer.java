/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;

import java.util.HashSet;
import java.util.Map;

/**
 * @author max
 */
public class LayeredLexer extends LexerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.LayeredLexer");
  private static final int IN_LAYER_STATE = 1024; // TODO: Other value?

  private CharSequence myBuffer;
  private int myBufferEnd;
  private int myState;

  private Lexer myBaseLexer;
  private Map<IElementType, Lexer> myStartTokenToLayerLexer = new HashMap<IElementType, Lexer>();
  private Lexer myCurrentLayerLexer;
  private HashSet<Lexer> mySelfStoppingLexers = new HashSet<Lexer>(1);
  private HashMap<Lexer, IElementType[]> myStopTokens = new HashMap<Lexer,IElementType[]>(1);


  public LayeredLexer(Lexer baseLexer) {
    myBaseLexer = baseLexer;
  }

  public void registerSelfStoppingLayer(Lexer Lexer, IElementType[] startTokens, IElementType[] stopTokens) {
    registerLayer(Lexer, startTokens);
    mySelfStoppingLexers.add(Lexer);
    myStopTokens.put(Lexer, stopTokens);
  }

  public void registerLayer(Lexer Lexer, IElementType... startTokens) {
    for (IElementType startToken : startTokens) {
      LOG.assertTrue(!myStartTokenToLayerLexer.containsKey(startToken));
      myStartTokenToLayerLexer.put(startToken, Lexer);
    }
  }

  private void activateLayerIfNecessary() {
    myCurrentLayerLexer = myStartTokenToLayerLexer.get(myBaseLexer.getTokenType());
    if (myCurrentLayerLexer != null) {
      myCurrentLayerLexer.start(myBaseLexer.getBufferSequence(), myBaseLexer.getTokenStart(), myBaseLexer.getTokenEnd(),0);
      if (mySelfStoppingLexers.contains(myCurrentLayerLexer)) {
        myBaseLexer.advance();
      }
    }
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    start(new CharArrayCharSequence(buffer),startOffset, endOffset, initialState);
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    LOG.assertTrue(initialState != IN_LAYER_STATE, "Restoring to layer is not supported.");
    myState = initialState;
    myCurrentLayerLexer = null;
    myBuffer = buffer;
    myBufferEnd = endOffset;
    myBaseLexer.start(buffer, startOffset, endOffset, initialState);
    activateLayerIfNecessary();
  }

  public int getState() {
    return myState;
  }

  public IElementType getTokenType() {
    return isLayerActive() ? myCurrentLayerLexer.getTokenType() : myBaseLexer.getTokenType();
  }

  public int getTokenStart() {
    return isLayerActive() ? myCurrentLayerLexer.getTokenStart() : myBaseLexer.getTokenStart();
  }

  public int getTokenEnd() {
    return isLayerActive() ? myCurrentLayerLexer.getTokenEnd() : myBaseLexer.getTokenEnd();
  }

  public void advance() {
    if (isLayerActive()) {
      IElementType layerTokenType = myCurrentLayerLexer.getTokenType();
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
          myBaseLexer.advance();
          activateLayerIfNecessary();
        } else {
          myCurrentLayerLexer = null;
          //myBaseLexer.start(myBuffer, tokenEnd, getBufferEnd());
        }
      }
    } else {
      myBaseLexer.advance();
      activateLayerIfNecessary();
    }
    myState = isLayerActive() ? IN_LAYER_STATE : myBaseLexer.getState();
  }

  public LexerPosition getCurrentPosition() {
    final int offset = getTokenStart();
    final int intState = getState();
    final LexerState state = new SimpleLexerState(intState);
    return new LexerPositionImpl(offset, state);
  }

  public void restore(LexerPosition position) {
    start(getBufferSequence(), position.getOffset(), getBufferEnd(), ((SimpleLexerState)position.getState()).getState());
  }

  private boolean isStopToken(Lexer lexer, IElementType tokenType) {
    final IElementType[] stopTokens = myStopTokens.get(lexer);
    if (stopTokens == null) return false;
    for (IElementType stopToken : stopTokens) {
      if (stopToken == tokenType) return true;
    }
    return false;
  }

  public char[] getBuffer() {
    return CharArrayUtil.fromSequence(myBuffer);
  }

  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myBufferEnd;
  }

  private boolean isLayerActive() {
    return myCurrentLayerLexer != null;
  }

  public Lexer getBaseLexer() {
    return myBaseLexer;
  }
}
