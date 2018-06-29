// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage.api;

import intellij.platform.onair.tree.BTree;
import org.jetbrains.annotations.NotNull;

public interface Storage extends StorageConsumer {

  @NotNull
  byte[] lookup(@NotNull Address address);

  @NotNull
  Address alloc(@NotNull byte[] what);

  void prefetch(@NotNull Address address, @NotNull byte[] bytes, @NotNull BTree tree, int size, byte type);
}
