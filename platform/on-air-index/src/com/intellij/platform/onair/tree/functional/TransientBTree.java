// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree.functional;

import com.intellij.platform.onair.storage.api.*;
import com.intellij.platform.onair.tree.BTree;
import com.intellij.platform.onair.tree.IPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO: epochs
public class TransientBTree implements TransientTree {

  final BTree storedTree;

  private final Object root;

  private final int keySize;

  private final Storage storage;

  public TransientBTree(BTree storedTree, Object root, int keySize, Storage storage) {
    this.storedTree = storedTree;
    this.root = root;
    this.keySize = keySize;
    this.storage = storage;
  }

  @Override
  public int getKeySize() {
    return keySize;
  }

  @Override
  public int getBase() {
    return BTree.DEFAULT_BASE;
  }

  @Nullable
  @Override
  public byte[] get(@NotNull Novelty.Accessor novelty, @NotNull byte[] key) {
    return root(novelty).get(novelty, key);
  }

  @Override
  public boolean forEach(@NotNull Novelty.Accessor novelty, @NotNull KeyValueConsumer consumer) {
    return root(novelty).forEach(novelty, consumer);
  }

  @Override
  public boolean forEach(@NotNull Novelty.Accessor novelty, @NotNull byte[] fromKey, @NotNull KeyValueConsumer consumer) {
    return root(novelty).forEach(novelty, fromKey, consumer);
  }

  @Override
  public TransientTree put(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @NotNull byte[] value) {
    return put(novelty, key, value, true);
  }

  @Override
  public TransientTree put(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @NotNull byte[] value, boolean overwrite) {
    final IPage page = root(novelty);
    final boolean[] result = new boolean[1];
    final IPage updatedPage = page.put(novelty, key, value, overwrite, result);
    if (result[0]) {
      return new TransientBTree(storedTree, updatedPage, keySize, storage);
    }
    else {
      return this;
    }
  }

  @Override
  public TransientTree delete(@NotNull Novelty.Accessor novelty, @NotNull byte[] key) {
    return delete(novelty, key, null);
  }

  @Override
  public TransientTree delete(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @Nullable byte[] value) {
    final IPage page = root(novelty);
    if (page.delete(novelty, key, value)) {
      return new TransientBTree(storedTree, page, keySize, storage);
    }
    else {
      return this;
    }
  }

  @Override
  public TransientTree flush() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  private IPage root(@NotNull Novelty.Accessor novelty) {
    long startAddress;
    if (root instanceof Address) {
      final Address rootAddress = (Address)root;
      if (rootAddress.isNovelty()) {
        startAddress = rootAddress.getLowBytes();
      }
      else {
        startAddress = Long.MIN_VALUE;
      }
      return new BTree(storage, keySize, rootAddress, startAddress).loadPage(novelty, rootAddress);
    } else {
      return (IPage)root;
    }
  }
}
