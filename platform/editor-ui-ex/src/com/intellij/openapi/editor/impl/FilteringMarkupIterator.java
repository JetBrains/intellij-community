// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.MarkupIterator;
import org.jetbrains.annotations.NotNull;

import java.util.NoSuchElementException;
import java.util.function.Predicate;

public class FilteringMarkupIterator<T> implements MarkupIterator<T> {
  @NotNull private final MarkupIterator<T> myDelegate;
  @NotNull private final Predicate<? super T> myFilter;

  public FilteringMarkupIterator(@NotNull MarkupIterator<T> delegate, @NotNull Predicate<? super T> filter) {
    myDelegate = delegate;
    myFilter = filter;
    skipUnrelated();
  }

  @Override
  public void dispose() {
    myDelegate.dispose();
  }

  @Override
  public T peek() throws NoSuchElementException {
    return myDelegate.peek();
  }

  @Override
  public boolean hasNext() {
    return myDelegate.hasNext();
  }

  @Override
  public T next() {
    T result = myDelegate.next();
    skipUnrelated();
    return result;
  }

  private void skipUnrelated() {
    while(myDelegate.hasNext() && !myFilter.test(myDelegate.peek())) myDelegate.next();
  }
}
