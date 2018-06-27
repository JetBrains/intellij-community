// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Novelty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class InternalPage extends BasePage {

  public InternalPage(byte[] backingArray, BTree tree, Address address, int size) {
    super(backingArray, tree, address, size);
  }

  @Override
  @Nullable
  protected byte[] get(@NotNull Novelty novelty, @NotNull byte[] key) {
    final int index = binarySearch(key, 0);
    return index < 0 ? getChild(novelty, Math.max(-index - 2, 0)).get(novelty, key) : getKey(novelty, index);
  }

  @Override
  @Nullable
  protected BasePage put(@NotNull Novelty novelty, @NotNull byte[] key, @NotNull byte[] value, boolean overwrite, boolean[] result) {
    int pos = binarySearch(key, 0);

    if (pos >= 0 && !overwrite) {
      // key found and overwrite is not possible - error
      return null;
    }

    if (pos < 0) {
      pos = -pos - 2;
      // if insert after last - set to last
      if (pos < 0) pos = 0;
    }

    final BasePage child = getChild(novelty, pos).getMutableCopy(novelty, tree);
    final BasePage newChild = child.put(novelty, key, value, overwrite, result);
    // change min key for child
    if (result[0]) {
      // TODO: tree.addExpired(childrenAddresses[pos]);
      if (!child.address.isNovelty()) {
        throw new IllegalStateException("child must be novelty");
      }
      set(pos, child.getMinKey(novelty), child.address.getLowBytes());
      if (newChild != null) {
        if (!newChild.address.isNovelty()) {
          throw new IllegalStateException("child must be novelty");
        }
        return insertAt(novelty, +1, newChild.getMinKey(novelty), newChild.address.getLowBytes());
      }
    }

    return null;
  }

  @Override
  protected BasePage split(@NotNull Novelty novelty, int from, int length) {
    final InternalPage result = copyOf(novelty, this, from, length);
    decrementSize(length);
    return result;
  }

  @Override
  protected InternalPage getMutableCopy(@NotNull Novelty novelty, BTree tree) {
    if (address.isNovelty()) {
      return this;
    }
    byte[] bytes = Arrays.copyOf(this.backingArray, backingArray.length);
    return new InternalPage(
      bytes,
      tree, new Address(novelty.alloc(bytes)), size
    );
  }

  @Override
  @NotNull
  public BasePage getChild(@NotNull Novelty novelty, final int index) {
    return tree.loadPage(novelty, getChildAddress(index));
  }

  private static InternalPage copyOf(@NotNull Novelty novelty, InternalPage page, int from, int length) {
    byte[] bytes = new byte[page.backingArray.length];

    final int bytesPerEntry = page.tree.getKeySize() + page.tree.getAddressSize();

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
