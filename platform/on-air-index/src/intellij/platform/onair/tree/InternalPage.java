// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Address;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class InternalPage extends BasePage {

  public InternalPage(byte[] backingArray, BTree tree, Address address, int size) {
    super(backingArray, tree, address, size);
  }

  @Override
  @Nullable
  protected byte[] get(@NotNull byte[] key) {
    final int index = binarySearch(key, 0);
    return index < 0 ? getChild(Math.max(-index - 2, 0)).get(key) : getKey(index);
  }

  @Override
  @Nullable
  protected BasePage put(@NotNull byte[] key, @NotNull byte[] value, boolean overwrite, boolean[] result) {
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

    final BasePage child = getChild(pos).getMutableCopy(tree);
    final BasePage newChild = child.put(key, value, overwrite, result);
    // change min key for child
    if (result[0]) {
      // TODO: tree.addExpired(childrenAddresses[pos]);
      set(pos, child.getMinKey(), child.address);
      if (newChild != null) {
        return insertAt(pos + 1, newChild.getMinKey(), newChild.address);
      }
    }

    return null;
  }

  @Override
  protected BasePage split(int from, int length) {
    final InternalPage result = copyOf(this, from, length);
    decrementSize(length);
    return result;
  }

  @Override
  protected InternalPage getMutableCopy(BTree tree) {
    if (address.isNovelty()) {
      return this;
    }
    byte[] bytes = Arrays.copyOf(this.backingArray, backingArray.length);
    return new InternalPage(
      bytes,
      tree, new Address(0, tree.alloc(bytes)), size
    );
  }

  @Override
  @NotNull
  public BasePage getChild(final int index) {
    return tree.loadPage(getChildAddress(index));
  }

  private static InternalPage copyOf(InternalPage page, int from, int length) {
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

    return new InternalPage(bytes, page.tree, new Address(0, page.tree.alloc(bytes)), length);
  }
}
