// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree.functional;

import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.tree.IPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public abstract class BaseTransientPage implements IPage {

  protected final byte[] backingArray; // keys only
  protected final TransientBTreePrototype tree;
  protected final long epoch;

  protected int size; // TODO: allow in-place update only for current epoch nodes

  protected BaseTransientPage(byte[] backingArray, TransientBTreePrototype tree, int size, long epoch) {
    this.backingArray = backingArray;
    this.tree = tree;
    this.size = size;
    this.epoch = epoch;
  }

  @Override
  public int getSize() {
    return size;
  }

  @Override
  public boolean isTransient() {
    return true;
  }

  @Override
  public void flush(@NotNull Novelty.Accessor novelty) {
    // TODO: remove
    // do nothing
  }

  @Override
  public long getMutableAddress() {
    throw new UnsupportedOperationException();
  }

  protected abstract IPage mergeWithChildren(@NotNull Novelty.Accessor novelty);

  @Override
  @NotNull
  public byte[] getMinKey() {
    if (size <= 0) {
      throw new ArrayIndexOutOfBoundsException("Page is empty.");
    }

    return Arrays.copyOf(backingArray, tree.keySize); // TODO: optimize
  }

  @Nullable
  public abstract BaseTransientPage put(@NotNull Novelty.Accessor novelty,
                                        long epoch,
                                        @NotNull byte[] key,
                                        @NotNull byte[] value,
                                        boolean overwrite,
                                        boolean[] result);

  public abstract boolean delete(@NotNull Novelty.Accessor novelty, long epoch, @NotNull byte[] key, @Nullable byte[] value);

  protected void incrementSize() {
    if (size >= tree.base) {
      throw new IllegalArgumentException("Can't increase tree page size");
    }
    size += 1;
  }

  protected void decrementSize(final int value) {
    if (size < value) {
      throw new IllegalArgumentException("Can't decrease tree page size " + size + " on " + value);
    }
    size -= value;
  }

  // WARNING: this method allocates an array
  protected byte[] getKey(int index) {
    final int bytesPerKey = tree.keySize;
    byte[] result = new byte[bytesPerKey];
    final int offset = bytesPerKey * index;
    System.arraycopy(backingArray, offset, result, 0, bytesPerKey);
    return result;
  }

  protected void mergeWith(BaseTransientPage page) {
    final int bytesPerKey = tree.keySize;
    System.arraycopy(page.backingArray, 0, backingArray, size * bytesPerKey, page.size);
    this.size += page.size;
  }
}
