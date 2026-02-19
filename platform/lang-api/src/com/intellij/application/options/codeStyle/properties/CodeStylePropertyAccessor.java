// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CodeStylePropertyAccessor<V> {
  public abstract boolean set(@NotNull V extVal);

  public abstract @Nullable V get();

  public final boolean setFromString(@NotNull String valueString) {
    V extValue = parseString(valueString);
    if (extValue != null) {
      return set(extValue);
    }
    return false;
  }

  protected abstract @Nullable V parseString(@NotNull String string);

  public final @Nullable String getAsString() {
    V value = get();
    return value != null ? valueToString(value) : null;
  }

  protected abstract @Nullable String valueToString(@NotNull V value);

  public abstract String getPropertyName();

  public boolean isIgnorable() {
    return false;
  }
}
