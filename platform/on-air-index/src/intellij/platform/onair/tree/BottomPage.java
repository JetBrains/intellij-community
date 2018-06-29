// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Arrays;

import static intellij.platform.onair.tree.BTree.BYTES_PER_ADDRESS;

public class BottomPage extends BasePage {

  public BottomPage(byte[] backingArray, BTree tree, Address address, int size) {
    super(backingArray, tree, address, size);
  }

  @Nullable
  @Override
  protected byte[] get(@NotNull Novelty novelty, @NotNull byte[] key) {
    final int index = binarySearch(key, 0);
    if (index >= 0) {
      return tree.loadLeaf(novelty, getChildAddress(index));
    }
    return null;
  }

  @Override
  protected boolean forEach(@NotNull Novelty novelty, @NotNull KeyValueConsumer consumer) {
    for (int i = 0; i < size; i++) {
      byte[] key = getKey(i);
      byte[] value = tree.loadLeaf(novelty, getChildAddress(i));
      if (!consumer.consume(key, value)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Override
  protected BasePage put(@NotNull Novelty novelty, @NotNull byte[] key, @NotNull byte[] value, boolean overwrite, boolean[] result) {
    int pos = binarySearch(key, 0);
    if (pos >= 0) {
      if (overwrite) {
        // key found
        final Address childAddress = getChildAddress(pos);
        if (childAddress.isNovelty()) {
          novelty.free(childAddress.getLowBytes());
        }
        final long childAddressLowBytes = novelty.alloc(value);
        setChild(pos, tree.getKeySize(), backingArray, childAddressLowBytes, 0);
        flush(novelty);

        // this should be always true in order to keep up with keysAddresses[pos] expiration
        result[0] = true;
      }
      return null;
    }

    // if found - insert at this position, else insert after found
    pos = -pos - 1;

    final BasePage page = insertAt(novelty, pos, key, novelty.alloc(value));
    result[0] = true;
    tree.incrementSize();
    return page;
  }

  @Override
  protected boolean delete(@NotNull Novelty novelty, @NotNull byte[] key, @Nullable byte[] value) {
    final int pos = binarySearch(key, 0);
    if (pos < 0) return false;

    // tree.addExpiredLoggable(keysAddresses[pos]);
    copyChildren(pos + 1, pos);
    tree.decrementSize();
    decrementSize(1);

    novelty.update(address.getLowBytes(), backingArray);
    return true;
  }

  @Override
  protected BottomPage split(@NotNull Novelty novelty, int from, int length) {
    final BottomPage result = copyOf(novelty, this, from, length);
    decrementSize(length);
    flush(novelty);
    return result;
  }

  @Override
  protected BottomPage getMutableCopy(@NotNull Novelty novelty, BTree tree) {
    if (address.isNovelty()) {
      return this;
    }
    byte[] bytes = Arrays.copyOf(this.backingArray, backingArray.length);
    return new BottomPage(
      bytes,
      tree, new Address(novelty.alloc(bytes)), size
    );
  }

  @Override
  protected Address save(@NotNull Novelty novelty, @NotNull Storage storage, @NotNull StorageConsumer consumer) {
    final byte[] resultBytes = Arrays.copyOf(backingArray, backingArray.length);
    for (int i = 0; i < size; i++) {
      Address childAddress = getChildAddress(i);
      if (childAddress.isNovelty()) {
        final byte[] leaf = novelty.lookup(childAddress.getLowBytes()); // leaf values are immutable by design
        childAddress = storage.alloc(leaf);
        consumer.store(childAddress, leaf);
        setChild(i, tree.getKeySize(), resultBytes, childAddress.getLowBytes(), childAddress.getHighBytes());
      }
    }
    Address result = storage.alloc(resultBytes);
    consumer.store(result, resultBytes);
    return result;
  }

  @Override
  protected BasePage getChild(@NotNull Novelty novelty, int index) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected BasePage mergeWithChildren(@NotNull Novelty novelty) {
    return this;
  }

  @Override
  protected boolean isBottom() {
    return true;
  }

  @Override
  protected void dump(@NotNull Novelty novelty, @NotNull PrintStream out, int level, BTree.ToString renderer) {
    indent(out, level + 1);
    out.println(getClass().getSimpleName());
    for (int i = 0; i < size; i++) {
      indent(out, level + 1);
      out.print("｜");
      indent(out, 3);
      out.println(
        renderer == null
        ? getClass().getSimpleName()
        : (renderer.renderKey(getKey(i)) + " → " + renderer.renderValue(getValue(novelty, i)))
      );
    }
  }

  private static BottomPage copyOf(@NotNull Novelty novelty, BottomPage page, int from, int length) {
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

    return new BottomPage(bytes, page.tree, new Address(novelty.alloc(bytes)), length);
  }
}
