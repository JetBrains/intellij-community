/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;

import java.util.LinkedList;

/**
 * @author peter
 */
public class MutableMarker {
  enum Mode { READY, DROPPED, COMMITTED, ERROR }

  private final PsiBuilder.Marker myStartMarker;
  private IElementType myResultType;
  private int myInitialPathLength;
  private LinkedList<IElementType> myPath;
  private Mode myMode;

  public MutableMarker(final LinkedList<IElementType> path, final PsiBuilder.Marker startMarker, final int initialPathLength) {
    myPath = path;
    myStartMarker = startMarker;
    myInitialPathLength = initialPathLength;
    myMode = Mode.READY;
  }

  public boolean isCommitted() {
    return myMode == Mode.COMMITTED;
  }

  public boolean isDropped() {
    return myMode == Mode.DROPPED;
  }

  public boolean isError() {
    return myMode == Mode.ERROR;
  }

  public boolean isReady() {
    return myMode == Mode.READY;
  }

  public MutableMarker setResultType(final IElementType resultType) {
    myResultType = resultType;
    return this;
  }

  public IElementType getResultType() {
    return myResultType;
  }

  public void finish() {
    if (myMode == Mode.READY) {
      if (myResultType == null) {
        myMode = Mode.DROPPED;
        myStartMarker.drop();
      }
      else {
        myMode = Mode.COMMITTED;
        myStartMarker.done(myResultType);
        restorePath();
        myPath.addLast(myResultType);
      }
    }
  }

  private void restorePath() {
    while (myPath.size() > myInitialPathLength) {
      myPath.removeLast();
    }
  }

  public MutableMarker precede() {
    return new MutableMarker(myPath, myStartMarker.precede(), myInitialPathLength);
  }

  public void finish(final IElementType type) {
    setResultType(type);
    finish();
  }

  public void drop() {
    assert myMode == Mode.READY : myMode;
    myMode = Mode.DROPPED;
    myStartMarker.drop();
  }

  public void rollback() {
    assert myMode == Mode.READY : myMode;
    myMode = Mode.DROPPED;
    restorePath();
    myStartMarker.rollbackTo();
  }

  public void error(final String message) {
    assert myMode == Mode.READY : myMode;
    myMode = Mode.ERROR;
    myStartMarker.error(message);
  }
}
