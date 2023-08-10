// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

/**
 * @deprecated See deprecation notice on {@link #delegatingPointer(Pointer, Object, Function)}.
 */
@Deprecated
final class HardPointerEq<T> implements Pointer<T> {

  private final T myValue;

  HardPointerEq(@NotNull T value) {
    myValue = value;
  }

  @Override
  public @NotNull T dereference() {
    return myValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HardPointerEq<?> pointer = (HardPointerEq<?>)o;
    return Objects.equals(myValue, pointer.myValue);
  }

  @Override
  public int hashCode() {
    return myValue.hashCode();
  }
}
