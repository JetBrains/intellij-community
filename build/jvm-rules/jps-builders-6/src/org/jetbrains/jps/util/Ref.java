// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Ref<T> {
  private T myValue;

  public Ref() { }

  public Ref(@Nullable T value) {
    myValue = value;
  }

  public final T get() {
    return myValue;
  }

  public final void set(@Nullable T value) {
    myValue = value;
  }

  @NotNull
  public static <T> Ref<T> create() {
    return new Ref<>();
  }

  public static <T> Ref<T> create(@Nullable T value) {
    return new Ref<>(value);
  }

  @Nullable
  public static <T> T deref(@Nullable Ref<T> ref) {
    return ref == null ? null : ref.get();
  }

  @Override
  public String toString() {
    return String.valueOf(myValue);
  }
}