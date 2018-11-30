// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree.functional;

import com.intellij.platform.onair.storage.api.*;
import com.intellij.platform.onair.tree.BTree;
import com.intellij.platform.onair.tree.BasePage;
import com.intellij.platform.onair.tree.IPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransientBTree implements TransientTree {

  final TransientBTreePrototype prototype;

  private final Object root;
  private final long epoch;

  private TransientBTree(@NotNull final TransientBTreePrototype prototype) {
    this.prototype = prototype;
    this.epoch = 0;
    this.root = new BottomTransientPage(new byte[prototype.base * prototype.keySize], prototype, 0, epoch, new Object[prototype.base]);
  }

  private TransientBTree(@NotNull final TransientBTreePrototype prototype, Object root, long epoch) {
    this.prototype = prototype;
    this.root = root;
    this.epoch = epoch;
  }

  @Override
  public int getKeySize() {
    return prototype.keySize;
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
    final boolean[] result = new boolean[1];
    final BaseTransientPage root = root(novelty).getTransientCopy(novelty, prototype, epoch);
    final BaseTransientPage newSibling = root.put(novelty, epoch, key, value, overwrite, result);
    final BaseTransientPage finalRoot;
    if (newSibling != null) {
      final byte[] bytes = new byte[getKeySize() * getBase()];
      final IPage[] children = new IPage[getBase()];
      final InternalTransientPage internalRoot = new InternalTransientPage(bytes, prototype, 2, epoch, children);
      TransientBTreeUtil.set(0, root.getMinKey(), getKeySize(), bytes);
      children[0] = root;
      TransientBTreeUtil.set(1, newSibling.getMinKey(), getKeySize(), bytes);
      children[1] = newSibling;
      finalRoot = internalRoot;
    }
    else {
      finalRoot = root;
    }
    return new TransientBTree(prototype, finalRoot, epoch);
  }

  @Override
  public TransientTree delete(@NotNull Novelty.Accessor novelty, @NotNull byte[] key) {
    return delete(novelty, key, null);
  }

  @Override
  public TransientTree delete(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @Nullable byte[] value) {
    final BaseTransientPage root = root(novelty).getTransientCopy(novelty, prototype, epoch);
    final IPage updatedRoot = TransientBTreeUtil.delete(novelty, epoch, root, key, value);
    if (root == updatedRoot) {
      return this;
    }
    else {
      // updatedRoot can be "downgraded" to stored page if some merge occurs
      Object finalRoot = updatedRoot.isTransient() ? updatedRoot : ((BasePage)updatedRoot).getAddress();
      return new TransientBTree(prototype, finalRoot, epoch);
    }
  }

  @Override
  public TransientTree flush() {
    if (!(root instanceof IPage)) {
      return this; // already on disk
    }

    // TODO: flush pages

    return new TransientBTree(prototype, root, epoch + 1);
  }

  @NotNull
  private IPage root(@NotNull Novelty.Accessor novelty) {
    if (root instanceof Address) {
      return loadRootPage(novelty);
    }
    else {
      return (IPage)root;
    }
  }

  private BasePage loadRootPage(@NotNull Novelty.Accessor novelty) {
    final long startAddress;
    final Address rootAddress = (Address)root;
    if (rootAddress.isNovelty()) {
      startAddress = rootAddress.getLowBytes();
    }
    else {
      startAddress = Long.MIN_VALUE;
    }
    return new BTree(prototype.storage, prototype.keySize, rootAddress, startAddress).loadPage(novelty, rootAddress);
  }

  // create a new empty tree
  public static TransientBTree create(final int keySize) {
    return new TransientBTree(new TransientBTreePrototype(keySize, BTree.DEFAULT_BASE));
  }

  // create a tree based on stored tree
  public static TransientBTree create(@NotNull final Address address, @NotNull Storage storage, final int keySize) {
    return new TransientBTree(new TransientBTreePrototype(BTree.load(storage, keySize, address), storage), address, 0);
  }
}
