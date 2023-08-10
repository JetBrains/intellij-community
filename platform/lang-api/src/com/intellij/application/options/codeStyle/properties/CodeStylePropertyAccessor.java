// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.properties;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CodeStylePropertyAccessor<V> {
  public abstract boolean set(@NotNull V extVal);

  @Nullable
  public abstract V get();

  public final boolean setFromString(@NotNull String valueString) {
    V extValue = parseString(valueString);
    if (extValue != null) {
      return set(extValue);
    }
    return false;
  }

  @Nullable
  protected abstract V parseString(@NotNull String string);

  @Nullable
  public final String getAsString() {
    V value = get();
    return value != null ? valueToString(value) : null;
  }

  @Nullable
  protected abstract String valueToString(@NotNull V value);

  public abstract String getPropertyName();

  public boolean isIgnorable() {
    return false;
  }
}
