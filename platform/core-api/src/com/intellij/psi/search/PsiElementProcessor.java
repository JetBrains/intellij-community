/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

    @NotNull
    public PsiElement[] toArray() {
      return PsiUtilCore.toPsiElementArray(myCollection);
    }

    @NotNull
    public Collection<T> getCollection() {
      return myCollection;
    }

    @NotNull
    public T[] toArray(T[] array) {
      return myCollection.toArray(array);
    }

    @Override
    public boolean execute(@NotNull T element) {
      myCollection.add(element);
      return true;
    }
  }

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

    @Nullable
    public T getFoundElement() {
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

  class FindFilteredElement<T extends PsiElement> extends FindElement<T> {
    private final PsiElementFilter myFilter;

    public FindFilteredElement(@NotNull PsiElementFilter filter) {
      myFilter = filter;
    }

    @Override
    public boolean execute(@NotNull T element) {
      return !myFilter.isAccepted(element) || super.execute(element);
    }
  }
}