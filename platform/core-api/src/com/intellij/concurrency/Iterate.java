package com.intellij.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * Author: dmitrylomov
 */
public abstract class Iterate<T> extends DoWhile {
  private final Iterator<T> myIterator;
  private boolean myIsDone;

  public Iterate(@NotNull Iterable<T> iterable) {
    myIterator = iterable.iterator();
    myIsDone = false;
  }

  @NotNull
  @Override
  protected final AsyncFuture<Boolean> body() {
    if (!myIterator.hasNext()) {
      myIsDone = true;
      return AsyncFutureFactory.wrap(true);
    }
    return process(myIterator.next());
  }

  @NotNull
  protected abstract AsyncFuture<Boolean> process(T t);

  @Override
  protected boolean condition() {
    return !myIsDone;
  }
}
