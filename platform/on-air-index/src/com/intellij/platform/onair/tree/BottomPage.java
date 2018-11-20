// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import com.intellij.platform.onair.storage.api.*;
import com.intellij.platform.onair.tree.functional.BaseTransientPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Arrays;

import static com.intellij.platform.onair.tree.BTree.BYTES_PER_ADDRESS;
import static com.intellij.platform.onair.tree.StoredBTreeUtil.indent;
import static com.intellij.platform.onair.tree.StoredBTreeUtil.set;
import static com.intellij.platform.onair.tree.StoredBTreeUtil.setChild;

public class BottomPage extends BasePage {
  protected int mask;

  public BottomPage(byte[] backingArray, BTree tree, Address address, int size, int mask) {
    super(backingArray, tree, address, size);
    this.mask = mask;
  }

  @Nullable
  @Override
  public byte[] get(@NotNull Novelty.Accessor novelty, @NotNull byte[] key) {
    final int index = BTreeCommon.binarySearch(backingArray, size, tree.getKeySize(), BYTES_PER_ADDRESS, key);
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
    for (int i = BTreeCommon.binarySearchRange(backingArray, size, tree.getKeySize(), BYTES_PER_ADDRESS, fromKey); i < size; i++) {
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
  public BasePage put(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @NotNull byte[] value, boolean overwrite, boolean[] result) {
    int pos = BTreeCommon.binarySearch(backingArray, size, tree.getKeySize(), BYTES_PER_ADDRESS, key);
    if (pos >= 0) {
      if (overwrite) {
        final int bytesPerEntry = tree.getKeySize() + BYTES_PER_ADDRESS;
        if (value.length < BYTES_PER_ADDRESS) {
          mask |= (1 << pos); // set mask bit
          updateMask(bytesPerEntry);
          setChild(pos, tree.getKeySize(), backingArray, value);
        }
        else {
          // key found
          /*if ((mask & (1L << pos)) == 0) {
            final Address childAddress = getChildAddress(pos);
            if (tree.canMutateInPlace(childAddress)) {
              novelty.free(childAddress.getLowBytes());
            }
          }*/

          final long childAddressLowBytes = novelty.alloc(value);
          mask &= ~(1 << pos); // drop mask bit
          updateMask(bytesPerEntry);
          setChild(pos, tree.getKeySize(), backingArray, childAddressLowBytes, 0);
        }
        flush(novelty);

        // this should be always true in order to keep up with keysAddresses[pos] expiration
        result[0] = true;
      }
      return null;
    }

    // if found - insert at this position, else insert after found
    pos = -pos - 1;

    final BasePage page;
    if (value.length < BYTES_PER_ADDRESS) {
      page = insertValueAt(novelty, pos, key, value);
    }
    else {
      page = BTreeCommon.insertAt(this, tree.getBase(), novelty, pos, key, value);
    }
    result[0] = true;
    tree.incrementSize();
    return page;
  }

  @Override
  public boolean delete(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @Nullable byte[] value) {
    final int pos = BTreeCommon.binarySearch(backingArray, size, tree.getKeySize(), BYTES_PER_ADDRESS, key);
    if (pos < 0) return false;

    // novelty.free(getChildAddress(pos));
    copyChildren(pos + 1, pos);
    tree.decrementSize();
    decrementSize(1);

    novelty.update(address.getLowBytes(), backingArray);
    return true;
  }

  @Override
  public BottomPage split(@NotNull Novelty.Accessor novelty, int from, int length) {
    final BottomPage result = copyOf(novelty, this, from, length);
    decrementSize(length);
    flush(novelty);
    return result;
  }

  @Override
  protected BottomPage getMutableCopy(@NotNull Novelty.Accessor novelty) {
    if (tree.canMutateInPlace(address)) {
      return this;
    }
    byte[] bytes = Arrays.copyOf(this.backingArray, backingArray.length);
    return new BottomPage(
      bytes,
      tree, new Address(novelty.alloc(bytes)), size, mask
    );
  }

  @Override
  public BaseTransientPage getTransientCopy(long epoch) {
    throw new UnsupportedOperationException(); // TODO
  }

  @Override
  protected Address save(@NotNull Novelty.Accessor novelty, @NotNull Storage storage, @NotNull StorageConsumer consumer) {
    final byte[] resultBytes = Arrays.copyOf(backingArray, backingArray.length);
    for (int i = 0; i < size; i++) {
      if ((mask & (1L << i)) == 0) {
        Address childAddress = getChildAddress(i);
        if (childAddress.isNovelty()) {
          final byte[] leaf = novelty.lookup(childAddress.getLowBytes()); // leaf values are immutable by design
          childAddress = storage.alloc(leaf);
          consumer.store(childAddress, leaf);
          setChild(i, tree.getKeySize(), resultBytes, childAddress.getLowBytes(), childAddress.getHighBytes());
        }
      }
    }
    Address result = storage.alloc(resultBytes);
    consumer.store(result, resultBytes);
    return result;
  }

  private void updateMask(int bytesPerEntry) {
    // TODO: optimize byte write?
    ByteUtils.writeUnsignedInt(mask ^ 0x80000000, backingArray, bytesPerEntry * tree.getBase() + 2);
  }

  private void setValue(int pos, byte[] key, byte[] value) {
    mask |= (1 << pos); // set mask bit
    updateMask(tree.getKeySize() + BYTES_PER_ADDRESS);
    final int bytesPerKey = tree.getKeySize();

    if (key.length != bytesPerKey) {
      throw new IllegalArgumentException("Invalid key length: need " + bytesPerKey + ", got: " + key.length);
    }

    set(pos, key, bytesPerKey, backingArray, value);
  }

  private BasePage insertValueAt(@NotNull Novelty.Accessor novelty, int pos, byte[] key, byte[] value) {
    if (!BTreeCommon.needSplit(this, tree.getBase())) {
      insertValueDirectly(novelty, pos, key, value);
      return null;
    }
    else {
      int splitPos = BTreeCommon.getSplitPos(this, pos);

      final BottomPage sibling = split(novelty, splitPos, size - splitPos);
      if (pos >= splitPos) {
        // insert into right sibling
        flush(novelty);
        sibling.insertValueAt(novelty, pos - splitPos, key, value);
      }
      else {
        // insert into self
        insertValueAt(novelty, pos, key, value);
      }
      return sibling;
    }
  }

  private void insertValueDirectly(@NotNull Novelty.Accessor novelty, final int pos, @NotNull byte[] key, @NotNull byte[] value) {
    if (pos < size) {
      copyChildren(pos, pos + 1);
    }
    setValue(pos, key, value);
    incrementSize();
    flush(novelty);
  }

  private byte[] getValue(@NotNull Novelty.Accessor novelty, int index) {
    if ((mask & (1L << index)) != 0) {
      return getInlineValue(index);
    }
    else {
      return tree.loadLeaf(novelty, getChildAddress(index));
    }
  }

  protected byte[] getInlineValue(int index) {
    final int keySize = tree.getKeySize();
    final int offset = (keySize + BYTES_PER_ADDRESS) * index + keySize;
    final int length = backingArray[offset + BYTES_PER_ADDRESS - 1] & 0xff;
    if (length >= BYTES_PER_ADDRESS) {
      throw new IllegalStateException("invalid length stored");
    }
    final byte[] result = new byte[length];
    System.arraycopy(backingArray, offset, result, 0, length);
    return result;
  }

  @Override
  public BasePage mergeWithChildren(@NotNull Novelty.Accessor novelty) {
    return this;
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

    mask &= ~(1 << pos); // drop mask bit
    updateMask(tree.getKeySize() + BYTES_PER_ADDRESS);

    set(pos, key, bytesPerKey, backingArray, novelty.alloc((byte[])child));

    incrementSize();
    flush(novelty);
  }

  @Override
  public boolean isBottom() {
    return true;
  }

  @Override
  protected void dump(@NotNull Novelty.Accessor novelty, @NotNull PrintStream out, int level, BTree.ToString renderer) {
    indent(out, level + 1);
    out.println(getClass().getSimpleName());
    for (int i = 0; i < size; i++) {
      indent(out, level + 1);
      out.print("｜");
      indent(out, 3);
      if (renderer == null) {
        out.println(
          getClass().getSimpleName()
        );
      }
      else {
        byte[] value = getValue(novelty, i);
        out.println(
          renderer.renderKey(getKey(i)) + " → " + renderer.renderValue(value)
        );
      }
    }
  }

  private void copyChildren(int from, int to) {
    int highBits = mask & (0xFFFFFFFF << from);
    int lowBits = mask & ~(0xFFFFFFFF << Math.min(from, to));

    this.mask = lowBits | highBits << (to - from);
    updateMask(tree.getKeySize() + BYTES_PER_ADDRESS);

    if (from >= size) return;

    final int bytesPerEntry = tree.getKeySize() + BYTES_PER_ADDRESS;

    System.arraycopy(
      backingArray, from * bytesPerEntry,
      backingArray, to * bytesPerEntry,
      (size - from) * bytesPerEntry
    );
  }

  private static BottomPage copyOf(@NotNull Novelty.Accessor novelty, BottomPage page, int from, int length) {
    byte[] bytes = new byte[page.backingArray.length];

    final int bytesPerEntry = page.tree.getKeySize() + BYTES_PER_ADDRESS;

    System.arraycopy(
      page.backingArray, from * bytesPerEntry,
      bytes, 0,
      length * bytesPerEntry
    );

    final int metadataOffset = bytesPerEntry * page.tree.getBase();

    bytes[metadataOffset] = BTree.BOTTOM;
    bytes[metadataOffset + 1] = (byte)length;

    final int copyMask = page.mask >>> from; // shift mask bits accordingly

    ByteUtils.writeUnsignedInt(copyMask ^ 0x80000000, bytes, metadataOffset + 2);

    return new BottomPage(bytes, page.tree, new Address(novelty.alloc(bytes)), length, copyMask);
  }
}
