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
  private LinkedList<IElementType> myPath;
  private boolean myFinished;

  public MutableMarker(final LinkedList<IElementType> path, final PsiBuilder.Marker startMarker, final int initialPathLength) {
    myPath = path;
    myStartMarker = startMarker;
    myInitialPathLength = initialPathLength;
  }

  public MutableMarker setResultType(final IElementType resultType) {
    myResultType = resultType;
    return this;
  }

  public IElementType getResultType() {
    return myResultType;
  }

  public void finish() {
    assert !myFinished;
    myFinished = true;

    if (myResultType == null) {
      myStartMarker.drop();
    } else {
      myStartMarker.done(myResultType);
      restorePath();
      myPath.addLast(myResultType);
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
    assert !myFinished;
    myFinished = true;
    myStartMarker.drop();
  }

  public void rollback() {
    assert !myFinished;
    myFinished = true;
    restorePath();
    myStartMarker.rollbackTo();
  }

  public void error(final String message) {
    assert !myFinished;
    myFinished = true;
    myStartMarker.error(message);
  }
}
