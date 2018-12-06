// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import com.intellij.platform.onair.storage.api.*;
import com.intellij.platform.onair.tree.functional.BaseTransientPage;
import com.intellij.platform.onair.tree.functional.InternalTransientPage;
import com.intellij.platform.onair.tree.functional.TransientBTreePrototype;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Arrays;

import static com.intellij.platform.onair.tree.BTree.BYTES_PER_ADDRESS;
import static com.intellij.platform.onair.tree.StoredBTreeUtil.indent;
import static com.intellij.platform.onair.tree.StoredBTreeUtil.setChild;

public class InternalPage extends BasePage implements IInternalPage {

  public InternalPage(byte[] backingArray, BTree tree, Address address, int size) {
    super(backingArray, tree, address, size);
  }

  @Override
  @Nullable
  public byte[] get(@NotNull Novelty.Accessor novelty, @NotNull byte[] key) {
    final int index = BTreeCommon.binarySearch(backingArray, size, tree.getKeySize(), BYTES_PER_ADDRESS, key);

    return index < 0 ? getChild(novelty, Math.max(-index - 2, 0)).get(novelty, key) : getChild(novelty, index).get(novelty, key);
  }

  @Override
  public boolean forEach(@NotNull Novelty.Accessor novelty, @NotNull KeyValueConsumer consumer) {
    for (int i = 0; i < size; i++) {
      BasePage child = getChild(novelty, i);
      if (!child.forEach(novelty, consumer)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean forEach(@NotNull Novelty.Accessor novelty, @NotNull byte[] fromKey, @NotNull KeyValueConsumer consumer) {
    final int fromIndex = BTreeCommon.binarySearchGuess(backingArray, size, tree.getKeySize(), BYTES_PER_ADDRESS, fromKey);

    return BTreeCommon.traverseInternalPage(this, novelty, fromIndex, fromKey, consumer);
  }

  @Override
  @Nullable
  public BasePage put(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @NotNull byte[] value, boolean overwrite, boolean[] result) {
    int pos = BTreeCommon.binarySearch(backingArray, size, tree.getKeySize(), BYTES_PER_ADDRESS, key);

    if (pos >= 0 && !overwrite) {
      // key found and overwrite is not possible - error
      return null;
    }

    if (pos < 0) {
      pos = -pos - 2;
      // if insert after last - set to last
      if (pos < 0) pos = 0;
    }

    final BasePage child = getChild(novelty, pos).getMutableCopy(novelty);
    final BasePage newChild = child.put(novelty, key, value, overwrite, result);
    // change min key for child
    if (result[0]) {
      set(pos, child.getMinKey(), child);
      if (newChild == null) {
        flush(novelty);
      }
      else {
        return BTreeCommon.insertAt(this, tree.getBase(), novelty, pos + 1, newChild.getMinKey(), newChild);
      }
    }

    return null;
  }

  @Override
  public boolean delete(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @Nullable byte[] value) {
    int pos = BTreeCommon.binarySearchGuess(backingArray, size, tree.getKeySize(), BYTES_PER_ADDRESS, key);
    final BasePage child = getChild(novelty, pos).getMutableCopy(novelty);
    if (!child.delete(novelty, key, value)) {
      return false;
    }
    // if first element was removed in child, then update min key
    final int childSize = child.size;
    if (childSize > 0) {
      set(pos, child.getMinKey(), child);
    }
    if (pos > 0) {
      final BasePage left = getChild(novelty, pos - 1);
      if (BTreeCommon.needMerge(left, child, tree.getBase())) {
        // merge child into left sibling
        // re-get mutable left
        getChild(novelty, pos - 1).getMutableCopy(novelty).mergeWith(child);
        removeChild(pos);
      }
    }
    else if (pos + 1 < size) {
      final BasePage right = getChild(novelty, pos + 1);
      if (BTreeCommon.needMerge(child, right, tree.getBase())) {
        // merge child with right sibling
        final BasePage mutableChild = child.getMutableCopy(novelty);
        mutableChild.mergeWith(getChild(novelty, pos + 1));
        removeChild(pos);
        // change key for link to right
        set(pos, mutableChild.getMinKey(), mutableChild);
      }
    }
    else if (childSize == 0) {
      removeChild(pos);
    }
    novelty.update(address.getLowBytes(), backingArray);
    return true;
  }

  @Override
  public IPage split(@NotNull Novelty.Accessor novelty, int from, int length) {
    final IPage result = copyOf(novelty, this, from, length);
    decrementSize(length);
    return result;
  }

  @Override
  protected InternalPage getMutableCopy(@NotNull Novelty.Accessor novelty) {
    if (tree.canMutateInPlace(address)) {
      return this;
    }
    byte[] bytes = Arrays.copyOf(this.backingArray, backingArray.length);
    return new InternalPage(
      bytes,
      tree, new Address(novelty.alloc(bytes)), size
    );
  }

  @Override
  public BaseTransientPage getTransientCopy(@NotNull Novelty.Accessor novelty,
                                            @NotNull TransientBTreePrototype tree,
                                            long epoch) {
    final int base = tree.base;
    final int keySize = tree.keySize;
    if (base != this.tree.getBase() || keySize != this.tree.getKeySize()) {
      throw new IllegalArgumentException("invalid tree");
    }

    byte[] bytes = new byte[base * keySize];
    Object[] children = new Object[base];

    for (int i = 0; i < size; i++) {
      final int keyOffset = (keySize + BYTES_PER_ADDRESS) * i;
      System.arraycopy(backingArray, keyOffset, bytes, keySize* i, keySize);
      children[i] = getChildAddress(i);
    }

    return new InternalTransientPage(bytes, tree, size, epoch, children);
  }

  @Override
  protected Address save(@NotNull Novelty.Accessor novelty, @NotNull Storage storage, @NotNull StorageConsumer consumer) {
    final byte[] resultBytes = Arrays.copyOf(backingArray, backingArray.length);
    for (int i = 0; i < size; i++) {
      Address childAddress = getChildAddress(i);
      if (childAddress.isNovelty()) {
        final BasePage child = tree.loadPage(novelty, childAddress);
        childAddress = child.save(novelty, storage, consumer);
        setChild(i, tree.getKeySize(), resultBytes, childAddress.getLowBytes(), childAddress.getHighBytes());
      }
    }
    Address result = storage.alloc(resultBytes);
    consumer.store(result, resultBytes);
    return result;
  }

  @Override
  @NotNull
  public BasePage getChild(@NotNull Novelty.Accessor novelty, final int index) {
    return tree.loadPage(novelty, getChildAddress(index));
  }

  @Override
  public BasePage mergeWithChildren(@NotNull Novelty.Accessor novelty) {
    BasePage result = this;
    while (!result.isBottom() && result.getSize() == 1) {
      result = ((InternalPage)result).getChild(novelty, 0);
    }
    return result;
  }

  @Override
  public void insertDirectly(@NotNull Novelty.Accessor novelty, final int pos, @NotNull byte[] key, Object child) {
    if (pos < size) {
      copyChildren(pos, pos + 1);
    }
    set(pos, key, (BasePage)child);
    incrementSize();
    flush(novelty);
  }

  @Override
  public boolean isBottom() {
    return false;
  }

  @Override
  protected void dump(@NotNull Novelty.Accessor novelty, @NotNull PrintStream out, int level, BTree.ToString renderer) {
    indent(out, level);
    out.println(getClass().getSimpleName());
    for (int i = 0; i < size; i++) {
      indent(out, level);
      out.print("• ");
      indent(out, 1);
      out.println(renderer == null ? getClass().getSimpleName() : (renderer.renderKey(getKey(i)) + " \\"));
      getChild(novelty, i).dump(novelty, out, level + 5, renderer);
    }
  }

  private void removeChild(int pos) {
    copyChildren(pos + 1, pos);
    decrementSize(1);
  }

  private void set(int pos, byte[] key, BasePage child) {
    final int bytesPerKey = tree.getKeySize();

    if (key.length != bytesPerKey) {
      throw new IllegalArgumentException("Invalid key length: need " + bytesPerKey + ", got: " + key.length);
    }

    StoredBTreeUtil.set(pos, key, bytesPerKey, backingArray, child.getMutableAddress());
  }

  private void copyChildren(final int from, final int to) {
    if (from >= size) return;

    final int bytesPerEntry = tree.getKeySize() + BYTES_PER_ADDRESS;

    System.arraycopy(
      backingArray, from * bytesPerEntry,
      backingArray, to * bytesPerEntry,
      (size - from) * bytesPerEntry
    );
  }

  private static InternalPage copyOf(@NotNull Novelty.Accessor novelty, InternalPage page, int from, int length) {
    byte[] bytes = new byte[page.backingArray.length];

    final int bytesPerEntry = page.tree.getKeySize() + BYTES_PER_ADDRESS;

    System.arraycopy(
      page.backingArray, from * bytesPerEntry,
      bytes, 0,
      length * bytesPerEntry
    );

    final int metadataOffset = bytesPerEntry * page.tree.getBase();

    bytes[metadataOffset] = BTree.INTERNAL;
    bytes[metadataOffset + 1] = (byte)length;

    return new InternalPage(bytes, page.tree, new Address(novelty.alloc(bytes)), length);
  }
}
