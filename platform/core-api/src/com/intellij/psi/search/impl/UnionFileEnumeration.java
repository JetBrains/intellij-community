// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.impl;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class UnionFileEnumeration implements VirtualFileEnumeration {
  private final @NotNull Collection<? extends VirtualFileEnumeration> myHints;

  public UnionFileEnumeration(@NotNull Collection<? extends VirtualFileEnumeration> hints) {
    myHints = hints;
  }

  @Override
  public boolean contains(int fileId) {
    for (VirtualFileEnumeration scope : myHints) {
      if (scope.contains(fileId)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int @NotNull [] asArray() {
    int[] result = ArrayUtil.EMPTY_INT_ARRAY;
    for (VirtualFileEnumeration hint : myHints) {
      int[] fileIds = hint.asArray();
      result = ArrayUtil.mergeArrays(result, fileIds);
    }
    return result;
  }
}
