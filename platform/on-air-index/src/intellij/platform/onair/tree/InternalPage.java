// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.Arrays;

import static intellij.platform.onair.tree.BTree.BYTES_PER_ADDRESS;

public class InternalPage extends BasePage {

  public InternalPage(byte[] backingArray, BTree tree, Address address, int size) {
    super(backingArray, tree, address, size);
  }

  @Override
  @Nullable
  protected byte[] get(@NotNull Novelty novelty, @NotNull byte[] key) {
    final int index = binarySearch(key, 0);
    return index < 0 ? getChild(novelty, Math.max(-index - 2, 0)).get(novelty, key) : getChild(novelty, index).get(novelty, key);
  }

  @Override
  protected boolean forEach(@NotNull Novelty novelty, @NotNull KeyValueConsumer consumer) {
    for (int i = 0; i < size; i++) {
      Address childAddress = getChildAddress(i);
      BasePage child = tree.loadPage(novelty, childAddress);
      if (!child.forEach(novelty, consumer)) {
        return false;
      }
    }
    return true;
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
      if (!child.address.isNovelty()) {
        throw new IllegalStateException("child must be novelty");
      }
      set(pos, child.getMinKey(), child.address.getLowBytes());
      if (newChild == null) {
        flush(novelty);
      }
      else {
        if (!newChild.address.isNovelty()) {
          throw new IllegalStateException("child must be novelty");
        }
        return insertAt(novelty, pos + 1, newChild.getMinKey(), newChild.address.getLowBytes());
      }
    }

    return null;
  }

  @Override
  protected boolean delete(@NotNull Novelty novelty, @NotNull byte[] key, @Nullable byte[] value) {
    int pos = binarySearchGuess(key);
    final BasePage child = getChild(novelty, pos).getMutableCopy(novelty, tree);
    if (!child.delete(novelty, key, value)) {
      return false;
    }
    // if first element was removed in child, then update min key
    final int childSize = child.size;
    if (childSize > 0) {
      set(pos, child.getMinKey(), child.address.getLowBytes());
    }
    if (pos > 0) {
      final BasePage left = getChild(novelty, pos - 1);
      if (needMerge(left, child)) {
        // merge child into left sibling
        // re-get mutable left
        getChild(novelty, pos - 1).getMutableCopy(novelty, tree).mergeWith(child);
        removeChild(pos);
      }
    }
    else if (pos + 1 < size) {
      final BasePage right = getChild(novelty, pos + 1);
      if (needMerge(child, right)) {
        // merge child with right sibling
        final BasePage mutableChild = child.getMutableCopy(novelty, tree);
        mutableChild.mergeWith(getChild(novelty, pos + 1));
        removeChild(pos);
        // change key for link to right
        set(pos, mutableChild.getMinKey(), mutableChild.address.getLowBytes());
      }
    }
    else if (childSize == 0) {
      removeChild(pos);
    }
    novelty.update(address.getLowBytes(), backingArray);
    return true;
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
  protected Address save(@NotNull Novelty novelty, @NotNull Storage storage, @NotNull StorageConsumer consumer) {
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
  protected BasePage getChild(@NotNull Novelty novelty, final int index) {
    return tree.loadPage(novelty, getChildAddress(index));
  }

  @Override
  protected BasePage mergeWithChildren(@NotNull Novelty novelty) {
    BasePage result = this;
    while (!result.isBottom() && result.size == 1) {
      result = result.getChild(novelty, 0);
    }
    return result;
  }

  protected void removeChild(int pos) {
    copyChildren(pos + 1, pos);
    decrementSize(1);
  }

  @Override
  protected boolean isBottom() {
    return false;
  }

  @Override
  protected void dump(@NotNull Novelty novelty, @NotNull PrintStream out, int level, BTree.ToString renderer) {
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

  private static InternalPage copyOf(@NotNull Novelty novelty, InternalPage page, int from, int length) {
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
