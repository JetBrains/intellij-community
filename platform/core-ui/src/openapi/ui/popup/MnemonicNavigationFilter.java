// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface MnemonicNavigationFilter<T> {
  int getMnemonicPos(T value);

  String getTextFor(T value);

  @NotNull List<T> getValues();

  @Nullable
  default String getMnemonicString(T value) {
    int pos = getMnemonicPos(value);
    if (pos == -1) return null;

    return getTextFor(value).substring(pos + 1, pos + 2);
  }
}
