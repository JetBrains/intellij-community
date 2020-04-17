// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.config;

import org.jetbrains.annotations.NonNls;

public class ValueProperty<T> extends AbstractProperty<T> {
  private final T myDefault;
  private final String myName;

  public ValueProperty(@NonNls String name, T defaultValue) {
    myName = name;
    myDefault = defaultValue;
  }

  @Override
  public T copy(T value) {
    return value;
  }

  @Override
  public T getDefault(AbstractProperty.AbstractPropertyContainer container) {
    return myDefault;
  }

  @Override
  public String getName() {
    return myName;
  }
}
