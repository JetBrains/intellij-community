// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree.functional;

import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.tree.IPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransientBTreeUtil {

  public static IPage delete(@NotNull Novelty.Accessor novelty,
                             long epoch,
                             @NotNull BaseTransientPage root,
                             @NotNull byte[] key,
                             @Nullable byte[] value) {
    if (root.delete(novelty, epoch, key, value)) {
      return root.mergeWithChildren(novelty);
    }

    return root;
  }

  public static void set(int pos, byte[] key, int bytesPerKey, byte[] backingArray) {
    final int offset = bytesPerKey * pos;

    // write key
    System.arraycopy(key, 0, backingArray, offset, bytesPerKey);
  }
}
