// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
final class IntersectionFileEnumeration implements VirtualFileEnumeration {
  private final @NotNull List<? extends VirtualFileEnumeration> myHints;

  IntersectionFileEnumeration(@NotNull List<? extends VirtualFileEnumeration> hints) {
    myHints = hints;
  }

  @Override
  public boolean contains(int fileId) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myHints.size(); i++) {
      VirtualFileEnumeration scope = myHints.get(i);
      if (!scope.contains(fileId)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int @NotNull [] asArray() {
    if (myHints.isEmpty()) return ArrayUtil.EMPTY_INT_ARRAY;
    if (myHints.size() == 1) return myHints.iterator().next().asArray();

    int[] result = null;
    for (VirtualFileEnumeration scope : myHints) {
      if (result == null) {
        result = scope.asArray();
      }
      else {
        result = ArrayUtil.intersection(result, scope.asArray());
      }
      if (result.length == 0) {
        return ArrayUtil.EMPTY_INT_ARRAY;
      }
    }
    return result;
  }
}
