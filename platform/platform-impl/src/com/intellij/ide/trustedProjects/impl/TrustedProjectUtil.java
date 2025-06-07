// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class TrustedProjectUtil {
  public static @NotNull List<Integer> findAllIndexesOfSymbol(@NotNull CharSequence charSequence, char character) {
    List<Integer> result = new ArrayList<>();
    int firstIndex = 0;
    int lastIndex = charSequence.length() - 1;

    for (int index = firstIndex; index <= lastIndex; index++) {
      char charAtIndex = charSequence.charAt(index);
      if (character == charAtIndex) {
        result.add(index);
      }
    }
    return result;
  }
}
