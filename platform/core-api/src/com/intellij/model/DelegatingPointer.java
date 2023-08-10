// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;

abstract class DelegatingPointer<T, U> implements Pointer<T> {

  private final @NotNull Pointer<? extends U> myUnderlyingPointer;

  protected DelegatingPointer(@NotNull Pointer<? extends U> underlyingPointer) {
    myUnderlyingPointer = underlyingPointer;
  }

  @Override
  public final @Nullable T dereference() {
    U underlyingValue = myUnderlyingPointer.dereference();
    return underlyingValue == null ? null : dereference(underlyingValue);
  }

  protected abstract T dereference(@NotNull U underlyingValue);

  static final class ByValue<T, U> extends DelegatingPointer<T, U> {

    private final @NotNull Function<? super U, ? extends T> myRestoration;

    ByValue(@NotNull Pointer<? extends U> underlyingPointer,
            @NotNull Function<? super U, ? extends T> restoration) {
      super(underlyingPointer);
      myRestoration = restoration;
    }

    @Override
    protected T dereference(@NotNull U underlyingValue) {
      return myRestoration.apply(underlyingValue);
    }
  }

  static final class ByValueAndPointer<T, U> extends DelegatingPointer<T, U> {

    private final @NotNull BiFunction<? super U, ? super Pointer<T>, ? extends T> myRestoration;

    ByValueAndPointer(@NotNull Pointer<? extends U> underlyingPointer,
                      @NotNull BiFunction<? super U, ? super Pointer<T>, ? extends T> restoration) {
      super(underlyingPointer);
      myRestoration = restoration;
    }

    @Override
    protected T dereference(@NotNull U underlyingValue) {
      return myRestoration.apply(underlyingValue, this);
    }
  }
}
