/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiElementFilter;

import java.util.ArrayList;
import java.util.Collection;

public interface PsiElementProcessor<T extends PsiElement> {
  boolean execute(T element);
  Object getHint(Class hintClass);


  class CollectElements<T extends PsiElement> extends PsiBaseElementProcessor<T> {
    private final Collection<T> myCollection;

    public CollectElements(Collection<T> collection) {
      myCollection = collection;
    }

    public CollectElements() {
      myCollection = new ArrayList<T>();
    }

    public PsiElement[] toArray() {
      return myCollection.toArray(new PsiElement[myCollection.size()]);
    }

    public Collection<T> getCollection() {
      return myCollection;
    }

    public T[] toArray(T[] array) {
      return myCollection.toArray(array);
    }

    public boolean execute(T element) {
      myCollection.add(element);
      return true;
    }
  }

  class CollectFilteredElements<T extends PsiElement> extends CollectElements<T> {
    private final PsiElementFilter myFilter;

    public CollectFilteredElements(PsiElementFilter filter, Collection<T> collection) {
      super(collection);
      myFilter = filter;
    }

    public CollectFilteredElements(PsiElementFilter filter) {
      myFilter = filter;
    }

    public boolean execute(T element) {
      if (myFilter.isAccepted(element)) return super.execute(element);
      return true;
    }
  }

  class CollectElementsWithLimit<T extends PsiElement> extends CollectElements<T>{
    private int myCount = 0;
    private boolean myOverflow = false;

    private final int myLimit;

    public CollectElementsWithLimit(int limit) {
      myLimit = limit;
    }

    public boolean execute(T element) {
      if (myCount == myLimit){
        myOverflow = true;
        return false;
      }
      myCount++;
      return super.execute(element);
    }

    public boolean isOverflow() {
      return myOverflow;
    }
  }

  class FindElement<T extends PsiElement> extends PsiBaseElementProcessor<T> {
    private T myFoundElement = null;

    public boolean isFound() {
      return myFoundElement != null;
    }

    public T getFoundElement() {
      return myFoundElement;
    }

    public boolean execute(T element) {
      myFoundElement = element;
      return false;
    }
  }

  class FindFilteredElement<T extends PsiElement> extends FindElement<T> {

    private final PsiElementFilter myFilter;

    public FindFilteredElement(PsiElementFilter filter) {
      myFilter = filter;
    }

    public boolean execute(T element) {
      if (myFilter.isAccepted(element)) return super.execute(element);
      return true;
    }
  }
}