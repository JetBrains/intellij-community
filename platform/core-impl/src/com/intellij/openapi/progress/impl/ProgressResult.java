// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class ProgressResult<T> {
  @Nullable
  private final T myResult;

  private final boolean myIsCanceled;
  @Nullable
  private final Throwable myThrowable;

  public ProgressResult(@Nullable T result, boolean canceled, @Nullable Throwable throwable) {
    myResult = result;
    myIsCanceled = canceled;
    myThrowable = throwable;
  }

  @Nullable
  public T getResult() {
    return myResult;
  }

  public boolean isCanceled() {
    return myIsCanceled;
  }

  @Nullable
  public Throwable getThrowable() {
    return myThrowable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ProgressResult<?> result = (ProgressResult<?>)o;
    return myIsCanceled == result.myIsCanceled &&
           Objects.equals(myResult, result.myResult) &&
           Objects.equals(myThrowable, result.myThrowable);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myResult, myIsCanceled, myThrowable);
  }
}
