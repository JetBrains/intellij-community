// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;


import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.KeyValueConsumer;
import intellij.platform.onair.storage.api.Novelty;
import intellij.platform.onair.storage.api.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Arrays;

import static intellij.platform.onair.tree.BTree.BYTES_PER_ADDRESS;
import static intellij.platform.onair.tree.ByteUtils.compare;
import static intellij.platform.onair.tree.ByteUtils.readUnsignedLong;
import static intellij.platform.onair.tree.ByteUtils.writeUnsignedLong;

public abstract class BasePage {
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

  @Nullable
  protected abstract byte[] get(@NotNull Novelty novelty, @NotNull final byte[] key);

  protected abstract BasePage getChild(@NotNull Novelty novelty, int index);

  @Nullable
  protected abstract BasePage put(@NotNull Novelty novelty,
                                  @NotNull byte[] key,
                                  @NotNull byte[] value,
                                  boolean overwrite,
                                  boolean[] result);

  protected abstract BasePage getMutableCopy(@NotNull Novelty novelty, BTree tree);

  protected abstract BasePage split(@NotNull Novelty novelty, int from, int length);

  protected abstract Address save(@NotNull final Novelty novelty, @NotNull final Storage storage);

  protected abstract void dump(@NotNull Novelty novelty, @NotNull PrintStream out, int level, BTree.ToString renderer);

  protected abstract boolean forEach(@NotNull Novelty novelty, @NotNull KeyValueConsumer consumer);

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

  protected byte[] getValue(@NotNull Novelty novelty, int index) {
    return tree.loadLeaf(novelty, getChildAddress(index));
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

  @NotNull
  protected byte[] getMinKey() {
    if (size <= 0) {
      throw new ArrayIndexOutOfBoundsException("Page is empty.");
    }

    return Arrays.copyOf(backingArray, tree.getKeySize()); // TODO: optimize
  }

  protected int binarySearch(byte[] key, int low) {
    final int bytesPerKey = tree.getKeySize();
    final int bytesPerEntry = bytesPerKey + BYTES_PER_ADDRESS;

    int high = size - 1;

    while (low <= high) {
      final int mid = (low + high) >>> 1;
      final int offset = mid * bytesPerEntry;

      final int cmp = compare(backingArray, bytesPerKey, offset, key, bytesPerKey, 0);
      if (cmp < 0) {
        low = mid + 1;
      }
      else if (cmp > 0) {
        high = mid - 1;
      }
      else {
        // key found
        return mid;
      }
    }
    // key not found
    return -(low + 1);
  }

  protected void flush(@NotNull Novelty novelty) {
    if (address.isNovelty()) {
      novelty.update(address.getLowBytes(), backingArray);
    }
  }

  protected void set(int pos, byte[] key, long lowAddressBytes) {
    final int bytesPerKey = tree.getKeySize();

    if (key.length != bytesPerKey) {
      throw new IllegalArgumentException("Invalid key length: need " + bytesPerKey + ", got: " + key.length);
    }

    set(pos, key, bytesPerKey, backingArray, lowAddressBytes);
  }

  protected void setChildAddress(int pos, long lowAddressBytes, long highAddressBytes) {
    final int bytesPerKey = tree.getKeySize();
    final int offset = (bytesPerKey + BYTES_PER_ADDRESS) * pos + bytesPerKey;

    // write address
    writeUnsignedLong(lowAddressBytes, 8, backingArray, offset);
    writeUnsignedLong(highAddressBytes, 8, backingArray, offset + 8);
  }

  protected BasePage insertAt(@NotNull Novelty novelty, int pos, byte[] key, long childAddress) {
    if (!needSplit(this)) {
      insertDirectly(novelty, pos, key, childAddress);
      return null;
    }
    else {
      int splitPos = getSplitPos(this, pos);

      final BasePage sibling = split(novelty, splitPos, size - splitPos);
      if (pos >= splitPos) {
        // insert into right sibling
        flush(novelty);
        sibling.insertAt(novelty, pos - splitPos, key, childAddress);
      }
      else {
        // insert into self
        insertAt(novelty, pos, key, childAddress);
      }
      return sibling;
    }
  }

  protected void insertDirectly(@NotNull Novelty novelty, final int pos, @NotNull byte[] key, long childAddress) {
    if (pos < size) {
      copyChildren(pos, pos + 1);
    }
    set(pos, key, childAddress);
    incrementSize();
    flush(novelty);
  }

  protected void copyChildren(final int from, final int to) {
    if (from >= size) return;

    final int bytesPerEntry = tree.getKeySize() + BYTES_PER_ADDRESS;

    System.arraycopy(
      backingArray, from * bytesPerEntry,
      backingArray, to * bytesPerEntry,
      (size - from) * bytesPerEntry
    );
  }

  private void setSize(int updatedSize) {
    final int sizeOffset = ((tree.getKeySize() + BYTES_PER_ADDRESS) * tree.getBase()) + 1;
    backingArray[sizeOffset] = (byte)updatedSize;
    this.size = updatedSize;
  }

  // TODO: extract Policy class
  public boolean needSplit(@NotNull final BasePage page) {
    return page.size >= tree.getBase();
  }

  // TODO: extract Policy class
  public int getSplitPos(@NotNull final BasePage page, final int insertPosition) {
    // if inserting into the most right position - split as 8/1, otherwise - 1/1
    final int pageSize = page.size;
    return insertPosition < pageSize ? pageSize >> 1 : (pageSize * 7) >> 3;
  }

  // TODO: extract Policy class
  public boolean needMerge(@NotNull final BasePage left, @NotNull final BasePage right) {
    final int leftSize = left.size;
    final int rightSize = right.size;
    return leftSize == 0 || rightSize == 0 || leftSize + rightSize <= ((tree.getBase() * 7) >> 3);
  }

  static void set(int pos, byte[] key, int bytesPerKey, byte[] backingArray, long lowAddressBytes) {
    final int offset = (bytesPerKey + BYTES_PER_ADDRESS) * pos;

    // write key
    System.arraycopy(key, 0, backingArray, offset, bytesPerKey);
    // write address
    writeUnsignedLong(lowAddressBytes, 8, backingArray, offset + bytesPerKey);
    writeUnsignedLong(0, 8, backingArray, offset + bytesPerKey + 8);
  }

  static void indent(PrintStream out, int level) {
    for (int i = 0; i < level; i++) out.print(" ");
  }
}
