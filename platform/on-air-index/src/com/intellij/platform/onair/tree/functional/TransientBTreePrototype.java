// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree.functional;

import com.intellij.platform.onair.storage.api.Storage;
import com.intellij.platform.onair.tree.BTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransientBTreePrototype {
  @Nullable
  public final BTree storedTree;
  @Nullable final Storage storage;
  public final int keySize;
  public final int base;

  /*package*/ TransientBTreePrototype(@NotNull BTree storedTree, @NotNull Storage storage) {
    this.storedTree = storedTree;
    this.storage = storage;
    this.keySize = storedTree.getKeySize();
    this.base = storedTree.getBase();
  }

  /*package*/ TransientBTreePrototype(final int keySize, final int base) {
    this.storedTree = null;
    this.storage = null;
    this.keySize = keySize;
    this.base = base;
  }
}
