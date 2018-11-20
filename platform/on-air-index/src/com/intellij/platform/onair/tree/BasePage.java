// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import com.intellij.platform.onair.storage.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Arrays;

import static com.intellij.platform.onair.tree.BTree.BYTES_PER_ADDRESS;
import static com.intellij.platform.onair.tree.ByteUtils.readUnsignedLong;

public abstract class BasePage implements IPage {
  protected final byte[] backingArray;
  protected final BTree tree;
  protected final Address address;

  protected int size;

  public BasePage(byte[] backingArray, BTree tree, Address address, int size) {
    this.backingArray = backingArray;
    this.tree = tree;
    this.address = address;
    this.size = size;
  }

  @Override
  public int getSize() {
    return size;
  }

  @Override
  @Nullable
  public abstract BasePage put(@NotNull Novelty.Accessor novelty,
                               @NotNull byte[] key,
                               @NotNull byte[] value,
                               boolean overwrite,
                               boolean[] result);

  @Override
  public void flush(@NotNull Novelty.Accessor novelty) {
    novelty.update(address.getLowBytes(), backingArray);
  }

  @Override
  public boolean isTransient() {
    return false;
  }

  @Override
  public long getMutableAddress() {
    if (!address.isNovelty()) {
      throw new IllegalStateException("address must be novelty");
    }
    return address.getLowBytes();
  }

  @Override
  @NotNull
  public byte[] getMinKey() {
    if (size <= 0) {
      throw new ArrayIndexOutOfBoundsException("Page is empty.");
    }

    return Arrays.copyOf(backingArray, tree.getKeySize()); // TODO: optimize
  }

  protected abstract BasePage getMutableCopy(@NotNull Novelty.Accessor novelty);

  protected abstract Address save(@NotNull final Novelty.Accessor novelty,
                                  @NotNull final Storage storage,
                                  @NotNull StorageConsumer consumer);

  protected abstract void dump(@NotNull Novelty.Accessor novelty, @NotNull PrintStream out, int level, BTree.ToString renderer);

  // WARNING: this method allocates an array
  protected byte[] getKey(int index) {
    final int bytesPerKey = tree.getKeySize();
    byte[] result = new byte[bytesPerKey];
    final int offset = (bytesPerKey + BYTES_PER_ADDRESS) * index;
    System.arraycopy(backingArray, offset, result, 0, bytesPerKey);
    return result;
  }

  protected Address getChildAddress(int index) {
    final int bytesPerKey = tree.getKeySize();
    final int offset = (bytesPerKey + BYTES_PER_ADDRESS) * index + bytesPerKey;
    final long lowBytes = readUnsignedLong(backingArray, offset, 8);
    final long highBytes = readUnsignedLong(backingArray, offset + 8, 8);
    return new Address(highBytes, lowBytes);
  }

  protected void incrementSize() {
    if (size >= tree.getBase()) {
      throw new IllegalArgumentException("Can't increase tree page size");
    }
    setSize(size + 1);
  }

  protected void decrementSize(final int value) {
    if (size < value) {
      throw new IllegalArgumentException("Can't decrease tree page size " + size + " on " + value);
    }
    setSize(size - value);
  }

  protected void mergeWith(BasePage page) {
    final int bytesPerEntry = tree.getKeySize() + BYTES_PER_ADDRESS;
    System.arraycopy(page.backingArray, 0, backingArray, size * bytesPerEntry, page.size);
    int length = page.size;
    this.size += length;
    final int metadataOffset = bytesPerEntry * page.tree.getBase();
    backingArray[metadataOffset + 1] = (byte)length;
  }

  protected void setSize(int updatedSize) {
    final int sizeOffset = ((tree.getKeySize() + BYTES_PER_ADDRESS) * tree.getBase()) + 1;
    backingArray[sizeOffset] = (byte)updatedSize;
    this.size = updatedSize;
  }
}
