// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

abstract class DelegatingPointer<T, U> implements Pointer<T> {

  private final @NotNull Pointer<? extends U> myUnderlyingPointer;
  private final @NotNull Object myKey;

  protected DelegatingPointer(@NotNull Pointer<? extends U> underlyingPointer, @NotNull Object key) {
    myUnderlyingPointer = underlyingPointer;
    myKey = key;
  }

  @Override
  public final @Nullable T dereference() {
    U underlyingValue = myUnderlyingPointer.dereference();
    return underlyingValue == null ? null : dereference(underlyingValue);
  }

  protected abstract T dereference(@NotNull U underlyingValue);

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DelegatingPointer<?, ?> base = (DelegatingPointer<?, ?>)o;
    return myKey.equals(base.myKey) && myUnderlyingPointer.equals(base.myUnderlyingPointer);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(myKey, myUnderlyingPointer);
  }

  static final class ByValue<T, U> extends DelegatingPointer<T, U> {

    private final @NotNull Function<? super U, ? extends T> myRestoration;

    ByValue(@NotNull Pointer<? extends U> underlyingPointer,
            @NotNull Object key,
            @NotNull Function<? super U, ? extends T> restoration) {
      super(underlyingPointer, key);
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
                      @NotNull Object key,
                      @NotNull BiFunction<? super U, ? super Pointer<T>, ? extends T> restoration) {
      super(underlyingPointer, key);
      myRestoration = restoration;
    }

    @Override
    protected T dereference(@NotNull U underlyingValue) {
      return myRestoration.apply(underlyingValue, this);
    }
  }
}
