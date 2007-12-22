/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.lang.pratt;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;

/**
 * @author peter
 */
public class MutableMarker {
  private final PsiBuilder.Marker myStartMarker;
  private IElementType myResultType;

  public MutableMarker(final PsiBuilder.Marker startMarker) {
    myStartMarker = startMarker;
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
    }
  }
  
  public MutableMarker precede() {
    return new MutableMarker(myStartMarker.precede());
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
    myStartMarker.rollbackTo();
  }
}
