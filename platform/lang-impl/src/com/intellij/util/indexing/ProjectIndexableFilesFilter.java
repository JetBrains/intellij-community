// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;

public final class ProjectIndexableFilesFilter extends IdFilter {
  private static final int SHIFT = 6;
  private static final int MASK = (1 << SHIFT) - 1;
  private final long[] myBitMask;
  private final int myModificationCount;
  private final int myMinId;
  private final int myMaxId;

  ProjectIndexableFilesFilter(@NotNull IntArrayList set, int modificationCount) {
    myModificationCount = modificationCount;
    final int[] minMax = new int[2];
    if (!set.isEmpty()) {
      minMax[0] = minMax[1] = set.getInt(0);
    }

    int[] elements = set.elements();
    for (int i = 0, n = set.size(); i < n; i++) {
      int value = elements[i];
      minMax[0] = Math.min(minMax[0], value);
      minMax[1] = Math.max(minMax[1], value);
    }
    myMaxId = minMax[1];
    myMinId = minMax[0];
    myBitMask = new long[((myMaxId - myMinId) >> SHIFT) + 1];
    for (int i = 0, n = set.size(); i < n; i++) {
      int value = elements[i] - myMinId;
      myBitMask[value >> SHIFT] |= (1L << (value & MASK));
    }
  }

  @Override
  public boolean containsFileId(int id) {
    if (id < myMinId) return false;
    if (id > myMaxId) return false;
    id -= myMinId;
    return (myBitMask[id >> SHIFT] & (1L << (id & MASK))) != 0;
  }

  public int getModificationCount() {
    return myModificationCount;
  }
}
