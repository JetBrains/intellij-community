// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree.functional;

import com.intellij.platform.onair.storage.api.Address;
import com.intellij.platform.onair.storage.api.KeyValueConsumer;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.tree.BTreeCommon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BottomTransientPage extends BaseTransientPage {
  protected final Object[] values; // Address | byte[]

  public BottomTransientPage(byte[] backingArray, TransientBTree tree, int size, long epoch, Object[] values) {
    super(backingArray, tree, size, epoch);
    this.values = values;
  }

  @Nullable
  @Override
  public byte[] get(@NotNull Novelty.Accessor novelty, @NotNull byte[] key) {
    final int index = BTreeCommon.binarySearch(backingArray, size, tree.getKeySize(), 0, key);
    if (index >= 0) {
      return getValue(novelty, index);
    }
    return null;
  }

  @Override
  public boolean forEach(@NotNull Novelty.Accessor novelty, @NotNull KeyValueConsumer consumer) {
    for (int i = 0; i < size; i++) {
      byte[] key = getKey(i);
      byte[] value = getValue(novelty, i);
      if (!consumer.consume(key, value)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean forEach(@NotNull Novelty.Accessor novelty, @NotNull byte[] fromKey, @NotNull KeyValueConsumer consumer) {
    for (int i = BTreeCommon.binarySearchRange(backingArray, size, tree.getKeySize(), 0, fromKey); i < size; i++) {
      byte[] key = getKey(i);
      byte[] value = getValue(novelty, i);
      if (!consumer.consume(key, value)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Override
  public BaseTransientPage put(@NotNull Novelty.Accessor novelty,
                               @NotNull byte[] key,
                               @NotNull byte[] value,
                               boolean overwrite,
                               boolean[] result) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Override
  public boolean delete(Novelty.Accessor novelty, byte[] key, byte[] value) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Override
  public void insertDirectly(@NotNull Novelty.Accessor novelty, final int pos, @NotNull byte[] key, Object child) {
    if (pos < size) {
      copyChildren(pos, pos + 1);
    }

    final int bytesPerKey = tree.getKeySize();

    if (key.length != bytesPerKey) {
      throw new IllegalArgumentException("Invalid key length: need " + bytesPerKey + ", got: " + key.length);
    }

    setTransient(pos, key, (byte[])child);

    incrementSize();
    flush(novelty);
  }

  @Override
  public BottomTransientPage split(@NotNull Novelty.Accessor novelty, int from, int length) {
    final BottomTransientPage result = copyOf(this, epoch, from, length);
    decrementSize(length);
    flush(novelty);
    return result;
  }

  @Override
  public BottomTransientPage getTransientCopy() {
    final long treeEpoch = tree.getEpoch();
    if (this.epoch >= treeEpoch) {
      return this;
    } else {
      return copyOf(this, treeEpoch, 0, size);
    }
  }

  @Override
  public BaseTransientPage mergeWithChildren(@NotNull Novelty.Accessor novelty) {
    return this;
  }

  @Override
  public boolean isBottom() {
    return true;
  }

  private byte[] getValue(@NotNull Novelty.Accessor novelty, int index) {
    final Object child = values[index];
    if (child instanceof byte[]) {
      return (byte[])child;
    }
    final Address address = (Address)child;
    final boolean isNovelty = address.isNovelty();
    return isNovelty ? novelty.lookup(address.getLowBytes()) : tree.storedTree.getStorage().lookup(address);
  }

  private void setTransient(int pos, byte[] key, byte[] child) {
    final int bytesPerKey = tree.getKeySize();
    final int offset = bytesPerKey * pos;

    // write key
    System.arraycopy(key, 0, backingArray, offset, bytesPerKey);

    // write value
    values[pos] = child;
  }

  private void copyChildren(final int from, final int to) {
    if (from >= size) return;

    final int bytesPerKey = tree.getKeySize();

    // copy keys
    System.arraycopy(
      backingArray, from * bytesPerKey,
      backingArray, to * bytesPerKey,
      (size - from) * bytesPerKey
    );

    // copy values
    System.arraycopy(
      values, from,
      values, to,
      (size - from)
    );
  }

  private static BottomTransientPage copyOf(BottomTransientPage page, long epoch, int from, int length) {
    byte[] bytes = new byte[page.backingArray.length];

    final int bytesPerKey = page.tree.getKeySize();

    // copy keys
    System.arraycopy(
      page.backingArray, from * bytesPerKey,
      bytes, 0,
      length * bytesPerKey
    );

    Object[] values = new Object[page.values.length];

    // copy values
    System.arraycopy(
      page.values, from,
      values, 0,
      length
    );

    return new BottomTransientPage(bytes, page.tree, length, epoch, values);
  }
}
