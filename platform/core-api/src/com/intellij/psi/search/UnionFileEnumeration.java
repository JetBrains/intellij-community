// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
final class UnionFileEnumeration implements VirtualFileEnumeration {
  private final @NotNull List<? extends VirtualFileEnumeration> myHints;

  UnionFileEnumeration(@NotNull List<? extends VirtualFileEnumeration> hints) {
    myHints = hints;
    if (hints.size() < 2) {
      throw new IllegalArgumentException("expected >= 2 scopes but got: "+hints);
    }
  }

  @Override
  public boolean contains(int fileId) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myHints.size(); i++) {
      VirtualFileEnumeration scope = myHints.get(i);
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
