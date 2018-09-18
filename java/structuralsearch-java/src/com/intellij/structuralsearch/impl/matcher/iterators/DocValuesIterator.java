// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.iterators;

import com.intellij.dupLocator.iterators.NodeIterator;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.javadoc.PsiDocToken;
import com.intellij.psi.tree.IElementType;

import java.util.ArrayList;

/**
 * Iterates over java doc values tag
 */
public class DocValuesIterator extends NodeIterator {
  private int index;
  private final ArrayList<PsiElement> tokens = new ArrayList<>(2);
  private static final IElementType tokenType = JavaDocTokenType.DOC_COMMENT_DATA;

  public DocValuesIterator(PsiElement start) {
    for(PsiElement e = start; e != null; e = e.getNextSibling()) {
      if (e instanceof PsiDocTagValue) tokens.add(e);
      else if (e instanceof PsiDocToken && ((PsiDocToken)e).getTokenType() == tokenType) {
        tokens.add(e);
        e = advanceToNext(e);
      }
    }
  }

  // since doctag value may be inside doc comment we specially skip that nodes from list
  static PsiElement advanceToNext(PsiElement e) {
    PsiElement nextSibling = e.getNextSibling();
    if (nextSibling instanceof PsiDocTagValue) e = nextSibling;

    nextSibling = e.getNextSibling();

    if (nextSibling instanceof PsiDocToken &&
        ((PsiDocToken)nextSibling).getTokenType() == tokenType
       ) {
      e = nextSibling;
    }
    return e;
  }

  @Override
  public boolean hasNext() {
    return index >=0 && index < tokens.size();
  }

  @Override
  public PsiElement current() {
    return hasNext() ? tokens.get(index) : null;
  }

  @Override
  public void advance() {
    if (index < tokens.size()) ++ index;
  }

  @Override
  public void rewind() {
    if (index >= 0) --index;
  }

  @Override
  public void reset() {
    index = 0;
  }
}
