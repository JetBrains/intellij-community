// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Novelty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

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
  protected BasePage getChild(@NotNull Novelty novelty, int index) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  protected BasePage put(@NotNull Novelty novelty, @NotNull byte[] key, @NotNull byte[] value, boolean overwrite, boolean[] result) {
    int pos = binarySearch(key, 0);
    if (pos >= 0) {
      if (overwrite) {
        // key found
        // TODO: tree.addExpired(keysAddresses[pos]);
        set(pos, key, novelty.alloc(Arrays.copyOf(value, value.length)));
        // this should be always true in order to keep up with keysAddresses[pos] expiration
        result[0] = true;
      }
      return null;
    }

    // if found - insert at this position, else insert after found
    pos = -pos - 1;

    final BasePage page = insertAt(novelty, pos, key, novelty.alloc(Arrays.copyOf(value, value.length)));
    result[0] = true;
    tree.incrementSize();
    return page;
  }

  @Override
  protected BottomPage split(@NotNull Novelty novelty, int from, int length) {
    final BottomPage result = copyOf(novelty, this, from, length);
    decrementSize(length);
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

  private static BottomPage copyOf(@NotNull Novelty novelty, BottomPage page, int from, int length) {
    byte[] bytes = new byte[page.backingArray.length];

    final int bytesPerEntry = page.tree.getKeySize() + page.tree.getAddressSize();

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
