package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;

import java.util.*;

/**
 * @author max
 */
public class LayeredLexer implements Lexer, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lexer.LayeredLexer");
  private static final int IN_LAYER_STATE = Integer.MAX_VALUE;

  private char[] myBuffer;
  private int myBufferEnd;
  private int myState;

  private Lexer myBaseLexer;
  private Map<IElementType, Lexer> myStartTokenToLayerLexer = new HashMap<IElementType, Lexer>();
  private Lexer myCurrentLayerLexer;
  private ArrayList<Lexer> myLayerLexers = new ArrayList<Lexer>(1);
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

  public void registerLayer(Lexer Lexer, IElementType[] startTokens) {
    for (int i = 0; i < startTokens.length; i++) {
      LOG.assertTrue(!myStartTokenToLayerLexer.containsKey(startTokens[i]));
      myStartTokenToLayerLexer.put(startTokens[i], Lexer);
    }

    myLayerLexers.add(Lexer);
  }

  private void activateLayerIfNecessary() {
    myCurrentLayerLexer = myStartTokenToLayerLexer.get(myBaseLexer.getTokenType());
    if (myCurrentLayerLexer != null) {
      myCurrentLayerLexer.start(myBaseLexer.getBuffer(), myBaseLexer.getTokenStart(), myBaseLexer.getTokenEnd());
      if (mySelfStoppingLexers.contains(myCurrentLayerLexer)) {
        myBaseLexer.advance();
      }
    }
  }

  public void start(char[] buffer) {
    start(buffer, 0, buffer.length);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    start(buffer, startOffset, endOffset,  0);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
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

  public int getLastState() {
    return 0;
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
          myBaseLexer.start(myBuffer, tokenEnd, getBufferEnd());
        }
      }
    } else {
      myBaseLexer.advance();
      activateLayerIfNecessary();
    }
    myState = isLayerActive() ? IN_LAYER_STATE : myBaseLexer.getState();
  }

  private boolean isStopToken(Lexer lexer, IElementType tokenType) {
    final IElementType[] stopTokens = myStopTokens.get(lexer);
    if (stopTokens == null) return false;
    for (int i = 0; i < stopTokens.length; i++) {
      IElementType stopToken = stopTokens[i];
      if (stopToken == tokenType) return true;
    }
    return false;
  }

  public char[] getBuffer() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myBufferEnd;
  }

  public int getSmartUpdateShift() {
    if (!isLayerActive()) return myBaseLexer.getSmartUpdateShift();
    if (myCurrentLayerLexer.getSmartUpdateShift() == -1) return -1;
    if (myBaseLexer.getSmartUpdateShift() == -1) return -1;
    return Math.max(myBaseLexer.getSmartUpdateShift(), myCurrentLayerLexer.getSmartUpdateShift());
    //return mySmartUpdate;
  }

  public Object clone() {
    try {
      final LayeredLexer lexer = (LayeredLexer) super.clone();
      lexer.myBaseLexer = (Lexer) myBaseLexer.clone();
      lexer.myLayerLexers = new ArrayList<Lexer>(myLayerLexers.size());
      for (int i = 0; i < myLayerLexers.size(); i++) {
        lexer.myLayerLexers.add((Lexer) myLayerLexers.get(i).clone());
      }

      Collection<IElementType> tokens = myStartTokenToLayerLexer.keySet();
      for (Iterator<IElementType> iterator = tokens.iterator(); iterator.hasNext();) {
        IElementType value = iterator.next();
        lexer.myStartTokenToLayerLexer.put(value, lexer.myLayerLexers.get(myLayerLexers.indexOf(myStartTokenToLayerLexer.get(value))));
      }

      lexer.myCurrentLayerLexer = myCurrentLayerLexer == null
                                 ? null
                                 : lexer.myLayerLexers.get(myLayerLexers.indexOf(myCurrentLayerLexer));

      return lexer;
    } catch (CloneNotSupportedException e) {
      LOG.error(e);
    }
    return null;
  }

  private boolean isLayerActive() {
    return myCurrentLayerLexer != null;
  }
}
