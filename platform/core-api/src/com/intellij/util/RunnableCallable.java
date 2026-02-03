// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;

/**
 * An adapter from Runnable to Callable.
 */
public final class RunnableCallable implements Callable<Void> {
  private final Runnable myDelegate;

  public RunnableCallable(@NotNull Runnable delegate) {
    myDelegate = delegate;
  }

  @Override
  public Void call() {
    myDelegate.run();
    return null;
  }

  public @NotNull Runnable getDelegate() {
    return myDelegate;
  }

  @Override
  public String toString() {
    return myDelegate.toString();
  }
}
