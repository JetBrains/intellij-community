// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class ClassConditionKey<T> {
  private final Class<T> myConditionClass;

  private ClassConditionKey(@NotNull Class<T> aClass) {
    myConditionClass = aClass;
  }

  @Contract(value = "_ -> new", pure = true)
  public static <T> @NotNull ClassConditionKey<T> create(Class<T> aClass) {
    return new ClassConditionKey<>(aClass);
  }

  public boolean isInstance(Object o) {
    return myConditionClass.isInstance(o);
  }

  @Override
  public String toString() {
    return myConditionClass.getName();
  }
}
