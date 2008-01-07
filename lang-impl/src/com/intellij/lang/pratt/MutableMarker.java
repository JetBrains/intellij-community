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
  private final PsiBuilder.Marker myStartMarker;
  private IElementType myResultType;
  private int myInitialPathLength;
  private LinkedList<Object> myPath;

  public MutableMarker(final LinkedList<Object> path, final PsiBuilder.Marker startMarker, final int initialPathLength) {
    myPath = path;
    myStartMarker = startMarker;
    myInitialPathLength = initialPathLength;
  }

  public int getInitialPathLength() {
    return myInitialPathLength;
  }

  public MutableMarker setResultType(final IElementType resultType) {
    myResultType = resultType;
    return this;
  }

  public IElementType getResultType() {
    return myResultType;
  }

  public void finish() {
    if (myResultType == null) {
      myStartMarker.drop();
    } else {
      myStartMarker.done(myResultType);
      restorePath();
      myPath.addFirst(myResultType);
    }
  }

  public void restorePath() {
    while (myPath.size() > myInitialPathLength) {
      myPath.removeFirst();
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
    setResultType(null);
    myStartMarker.drop();
  }

  public void rollback() {
    restorePath();
    myStartMarker.rollbackTo();
  }

  public void error(final String message) {
    myStartMarker.error(message);
  }
}
