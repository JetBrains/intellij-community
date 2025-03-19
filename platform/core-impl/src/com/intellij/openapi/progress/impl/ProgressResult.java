// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl;

import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@Obsolete
public final class ProgressResult<T> {
  private final @Nullable T myResult;

  private final boolean myIsCanceled;
  private final @Nullable Throwable myThrowable;

  public ProgressResult(@Nullable T result, boolean canceled, @Nullable Throwable throwable) {
    myResult = result;
    myIsCanceled = canceled;
    myThrowable = throwable;
  }

  public @Nullable T getResult() {
    return myResult;
  }

  public boolean isCanceled() {
    return myIsCanceled;
  }

  public @Nullable Throwable getThrowable() {
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
