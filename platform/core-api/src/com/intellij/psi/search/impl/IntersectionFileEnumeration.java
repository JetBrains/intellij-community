// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.impl;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
public final class IntersectionFileEnumeration implements VirtualFileEnumeration {
  private final @NotNull Collection<? extends VirtualFileEnumeration> myHints;

  public IntersectionFileEnumeration(@NotNull Collection<? extends VirtualFileEnumeration> hints) {
    myHints = hints;
  }

  @Override
  public boolean contains(int fileId) {
    if (myHints.isEmpty()) return false;
    for (VirtualFileEnumeration scope : myHints) {
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
