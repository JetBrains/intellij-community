// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import com.intellij.platform.onair.storage.api.KeyValueConsumer;
import com.intellij.platform.onair.storage.api.Novelty;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.onair.tree.ByteUtils.compare;

public class BTreeCommon {

  public static boolean traverseInternalPage(@NotNull final IInternalPage page,
                                             @NotNull Novelty.Accessor novelty,
                                             int fromIndex,
                                             @NotNull byte[] fromKey,
                                             @NotNull KeyValueConsumer consumer) {
    boolean first = true;
    for (int i = fromIndex; i < page.getSize(); i++) {
      IPage child = page.getChild(novelty, i);
      if (first) {
        if (!child.forEach(novelty, fromKey, consumer)) {
          return false;
        }
        first = false;
      }
      else {
        if (!child.forEach(novelty, consumer)) {
          return false;
        }
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  public static <T extends IPage> T insertAt(@NotNull T page,
                                             int base,
                                             @NotNull Novelty.Accessor novelty,
                                             int pos,
                                             byte[] key,
                                             Object child) {
    if (!needSplit(page, base)) {
      page.insertDirectly(novelty, pos, key, child);
      return null;
    }
    else {
      int splitPos = getSplitPos(page, pos);

      final T sibling = (T)page.split(novelty, splitPos, page.getSize() - splitPos);
      if (pos >= splitPos) {
        // insert into right sibling
        page.flush(novelty);
        insertAt(sibling, base, novelty, pos - splitPos, key, child);
      }
      else {
        // insert into self
        insertAt(sibling, base, novelty, pos, key, child);
      }
      return sibling;
    }
  }

  // TODO: extract Policy class
  public static boolean needSplit(@NotNull final IPage page, final int base) {
    return page.getSize() >= base;
  }

  // TODO: extract Policy class
  public static int getSplitPos(@NotNull final IPage page, final int insertPosition) {
    // if inserting into the most right position - split as 8/1, otherwise - 1/1
    final int pageSize = page.getSize();
    return insertPosition < pageSize ? pageSize >> 1 : (pageSize * 7) >> 3;
  }

  // TODO: extract Policy class
  public static boolean needMerge(@NotNull final IPage left, @NotNull final IPage right, final int base) {
    final int leftSize = left.getSize();
    final int rightSize = right.getSize();
    return leftSize == 0 || rightSize == 0 || leftSize + rightSize <= ((base * 7) >> 3);
  }

  public static int binarySearchGuess(byte[] backingArray, int size, int bytesPerKey, int bytesPerAddress, byte[] key) {
    int index = binarySearch(backingArray, size, bytesPerKey, bytesPerAddress, key);
    if (index < 0) {
      index = Math.max(0, -index - 2);
    }
    return index;
  }

  public static int binarySearchRange(byte[] backingArray, int size, int bytesPerKey, int bytesPerAddress, byte[] key) {
    int index = binarySearch(backingArray, size, bytesPerKey, bytesPerAddress, key);
    if (index < 0) {
      index = Math.max(0, -index - 1);
    }
    return index;
  }

  public static int binarySearch(byte[] backingArray, int size, int bytesPerKey, int bytesPerAddress, byte[] key) {
    final int bytesPerEntry = bytesPerKey + bytesPerAddress;

    int low = 0;
    int high = size - 1;

    while (low <= high) {
      final int mid = (low + high) >>> 1;
      final int offset = mid * bytesPerEntry;

      final int cmp = compare(backingArray, bytesPerKey, offset, key, bytesPerKey, 0);
      if (cmp < 0) {
        low = mid + 1;
      }
      else if (cmp > 0) {
        high = mid - 1;
      }
      else {
        // key found
        return mid;
      }
    }
    // key not found
    return -(low + 1);
  }
}
