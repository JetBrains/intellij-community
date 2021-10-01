// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.impl;

import com.intellij.openapi.vfs.CompactVirtualFileSet;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

@ApiStatus.Internal
public final class IntersectionFileEnumeration implements VirtualFileEnumeration {
  private final @NotNull Collection<VirtualFileEnumeration> myHints;

  public IntersectionFileEnumeration(@NotNull Collection<VirtualFileEnumeration> hints) {
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
  public int[] asInts() {
    Iterable<VirtualFile> files = asIterable();
    if (files instanceof Collection && ((Collection<VirtualFile>)files).isEmpty()) {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }
    if (files instanceof CompactVirtualFileSet) {
      return ((CompactVirtualFileSet)files).onlyFileIds();
    }

    IntList result = new IntArrayList();
    for (VirtualFile file : files) {
      if (file instanceof VirtualFileWithId) {
        int fileId = ((VirtualFileWithId)file).getId();
        result.add(fileId);
      }
    }
    return result.toIntArray();
  }

  @Override
  public @NotNull Iterable<VirtualFile> asIterable() {
    if (myHints.isEmpty()) return Collections.emptySet();
    if (myHints.size() == 1) return myHints.iterator().next().asIterable();
    CompactVirtualFileSet files = null;
    for (VirtualFileEnumeration scope : myHints) {
      Collection<VirtualFile> it = ContainerUtil.toCollection(scope.asIterable());
      if (files == null) {
        files = new CompactVirtualFileSet(it);
      }
      else {
        files.retainAll(it);
      }
    }
    return files;
  }
}
