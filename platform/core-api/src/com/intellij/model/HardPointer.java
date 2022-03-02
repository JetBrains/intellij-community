// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

final class HardPointer<T> implements Pointer<T> {

  private final T myValue;

  HardPointer(@NotNull T value) {
    myValue = value;
  }

  @NotNull
  @Override
  public T dereference() {
    return myValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HardPointer<?> pointer = (HardPointer<?>)o;
    return Objects.equals(myValue, pointer.myValue);
  }

  @Override
  public int hashCode() {
    return myValue.hashCode();
  }
}
