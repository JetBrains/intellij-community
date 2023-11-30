// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiElementFilter;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @see com.intellij.psi.util.PsiTreeUtil#processElements(PsiElement, PsiElementProcessor)
 */
public interface PsiElementProcessor<T extends PsiElement> {
  /**
   * Processes a PsiElement
   *
   * @param element currently processed element.
   * @return false to stop processing.
   */
  boolean execute(@NotNull T element);

  class CollectElements<T extends PsiElement> implements PsiElementProcessor<T> {
    private final Collection<T> myCollection;

    public CollectElements() {
      this(new ArrayList<>());
    }

    public CollectElements(@NotNull Collection<T> collection) {
      myCollection = Collections.synchronizedCollection(collection);
    }

    public PsiElement @NotNull [] toArray() {
      return PsiUtilCore.toPsiElementArray(myCollection);
    }

    public @NotNull Collection<T> getCollection() {
      return myCollection;
    }

    public T @NotNull [] toArray(T[] array) {
      return myCollection.toArray(array);
    }

    @Override
    public boolean execute(@NotNull T element) {
      myCollection.add(element);
      return true;
    }
  }

  /**
   * @deprecated use {@link com.intellij.psi.SyntaxTraverser} API instead. E.g.
   * {@code SyntaxTraverser.psiTraverser(root).filter(ElementType.class).filter(additionalFilter).toList()}
   */
  @Deprecated
  class CollectFilteredElements<T extends PsiElement> extends CollectElements<T> {
    private final PsiElementFilter myFilter;

    public CollectFilteredElements(@NotNull PsiElementFilter filter, @NotNull Collection<T> collection) {
      super(collection);
      myFilter = filter;
    }

    public CollectFilteredElements(@NotNull PsiElementFilter filter) {
      myFilter = filter;
    }

    @Override
    public boolean execute(@NotNull T element) {
      return !myFilter.isAccepted(element) || super.execute(element);
    }
  }

  class CollectElementsWithLimit<T extends PsiElement> extends CollectElements<T>{
    private final AtomicInteger myCount = new AtomicInteger(0);
    private volatile boolean myOverflow;
    private final int myLimit;

    public CollectElementsWithLimit(int limit) {
      myLimit = limit;
    }

    public CollectElementsWithLimit(int limit, @NotNull Collection<T> collection) {
      super(collection);
      myLimit = limit;
    }

    @Override
    public boolean execute(@NotNull T element) {
      if (myCount.get() == myLimit){
        myOverflow = true;
        return false;
      }
      myCount.incrementAndGet();
      return super.execute(element);
    }

    public boolean isOverflow() {
      return myOverflow;
    }
  }

  class FindElement<T extends PsiElement> implements PsiElementProcessor<T> {
    private volatile T myFoundElement;

    public boolean isFound() {
      return myFoundElement != null;
    }

    public @Nullable T getFoundElement() {
      return myFoundElement;
    }

    public boolean setFound(T element) {
      myFoundElement = element;
      return false;
    }

    @Override
    public boolean execute(@NotNull T element) {
      return setFound(element);
    }
  }
}