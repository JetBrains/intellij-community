// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

@ApiStatus.Experimental
public abstract class CodeStylePropertyAccessor<T> {
  private final Object myObject;
  private final Field myField;

  public CodeStylePropertyAccessor(@NotNull Object object, @NotNull Field field) {
    myObject = object;
    myField = field;
  }

  public void set(@NotNull String str) {
    try {
      T value = parseString(str);
      if (value != null) {
        myField.set(myObject, value);
      }
    }
    catch (IllegalAccessException e) {
      // Ignore and skip
    }
  }

  @Nullable
  public String get() {
    try {
      //noinspection unchecked
      T value = (T)myField.get(myObject);
      return !isEmpty(value) ? asString(value) : null;
    }
    catch (IllegalAccessException e) {
      // Ignore and return null
    }
    return null;
  }

  @NotNull
  public Class getObjectClass() {
    return myObject.getClass();
  }

  protected boolean isEmpty(@NotNull T value) {
    return false;
  }

  @Nullable
  protected abstract T parseString(@NotNull String str);

  @NotNull
  protected abstract String asString(@NotNull T value);
}
