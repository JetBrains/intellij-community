// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree.functional;

import com.intellij.platform.onair.storage.api.Storage;
import com.intellij.platform.onair.tree.BTree;

/*package*/ class TransientBTreePrototype {
  final BTree storedTree;
  final Storage storage;
  final int keySize;
  final int base;

  /*package*/ TransientBTreePrototype(BTree storedTree, Storage storage) {
    this.storedTree = storedTree;
    this.storage = storage;
    this.keySize = storedTree.getKeySize();
    this.base = storedTree.getBase();
  }
}
