/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

import com.intellij.psi.PsiReference;

import java.util.ArrayList;
import java.util.Collection;

public interface PsiReferenceProcessor{
  boolean execute(PsiReference element);

  class CollectElements implements PsiReferenceProcessor{
    private final Collection<PsiReference> myCollection;

    public CollectElements(Collection<PsiReference> collection) {
      myCollection = collection;
    }

    public CollectElements() {
      myCollection = new ArrayList<PsiReference>();
    }

    public PsiReference[] toArray(){
      return myCollection.toArray(new PsiReference[myCollection.size()]);
    }

    public PsiReference[] toArray(PsiReference[] array){
      return myCollection.toArray(array);
    }

    public boolean execute(PsiReference element) {
      myCollection.add(element);
      return true;
    }
  }

  class FindElement implements PsiReferenceProcessor{
    private PsiReference myFoundElement = null;

    public boolean isFound() {
      return myFoundElement != null;
    }

    public PsiReference getFoundReference() {
      return myFoundElement;
    }

    public boolean execute(PsiReference element) {
      myFoundElement = element;
      return false;
    }
  }
}