// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree.functional;

import com.intellij.platform.onair.storage.api.Address;
import com.intellij.platform.onair.storage.api.KeyValueConsumer;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.tree.BTreeCommon;
import com.intellij.platform.onair.tree.IInternalPage;
import com.intellij.platform.onair.tree.IPage;
import com.intellij.platform.onair.tree.InternalPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InternalTransientPage extends BaseTransientPage implements IInternalPage {

  protected final IPage[] children; // Address | IPage

  public InternalTransientPage(byte[] backingArray, TransientBTree tree, int size, IPage[] children) {
    super(backingArray, tree, size);
    this.children = children;
  }

  @Override
  @Nullable
  public byte[] get(@NotNull Novelty.Accessor novelty, @NotNull byte[] key) {
    final int index = BTreeCommon.binarySearch(backingArray, size, tree.getKeySize(), 0, key);

    return index < 0 ? getChild(novelty, Math.max(-index - 2, 0)).get(novelty, key) : getChild(novelty, index).get(novelty, key);
  }

  @Override
  public boolean forEach(@NotNull Novelty.Accessor novelty, @NotNull KeyValueConsumer consumer) {
    for (int i = 0; i < size; i++) {
      IPage child = getChild(novelty, i);
      if (!child.forEach(novelty, consumer)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean forEach(@NotNull Novelty.Accessor novelty, @NotNull byte[] fromKey, @NotNull KeyValueConsumer consumer) {
    final int fromIndex = BTreeCommon.binarySearchGuess(backingArray, size, tree.getKeySize(), 0, fromKey);

    return BTreeCommon.traverseInternalPage(this, novelty, fromIndex, fromKey, consumer);
  }

  @Override
  @Nullable
  public BaseTransientPage put(@NotNull Novelty.Accessor novelty,
                               @NotNull byte[] key,
                               @NotNull byte[] value,
                               boolean overwrite,
                               boolean[] result) {
    int pos = BTreeCommon.binarySearch(backingArray, size, tree.getKeySize(), 0, key);

    if (pos >= 0 && !overwrite) {
      // key found and overwrite is not possible - error
      return null;
    }

    if (pos < 0) {
      pos = -pos - 2;
      // if insert after last - set to last
      if (pos < 0) pos = 0;
    }

    final IPage child = getChild(novelty, pos).getTransientCopy();
    final IPage newChild = child.put(novelty, key, value, overwrite, result);
    // change min key for child
    if (result[0]) {
      setTransient(pos, child.getMinKey(), child);
      if (newChild == null) {
        flush(novelty);
      }
      else {
        return BTreeCommon.insertAt(this, tree.getBase(), novelty, pos + 1, newChild.getMinKey(), newChild);
      }
    }

    return null;
  }

  @Override
  public boolean delete(@NotNull Novelty.Accessor novelty, @NotNull byte[] key, @Nullable byte[] value) {
    int pos = BTreeCommon.binarySearchGuess(backingArray, size, tree.getKeySize(), 0, key);
    final BaseTransientPage child = getChild(novelty, pos).getTransientCopy();
    if (!child.delete(novelty, key, value)) {
      return false;
    }
    // if first element was removed in child, then update min key
    final int childSize = child.getSize();
    if (childSize > 0) {
      setTransient(pos, child.getMinKey(), child);
    }
    if (pos > 0) {
      final IPage left = getChild(novelty, pos - 1);
      if (BTreeCommon.needMerge(left, child, tree.getBase())) {
        // merge child into left sibling
        // re-get mutable left
        getChild(novelty, pos - 1).getTransientCopy().mergeWith(child);
        removeChild(pos);
      }
    }
    else if (pos + 1 < size) {
      final IPage right = getChild(novelty, pos + 1);
      if (BTreeCommon.needMerge(child, right, tree.getBase())) {
        // merge child with right sibling
        final BaseTransientPage mutableChild = child.getTransientCopy();
        IPage sibling = getChild(novelty, pos + 1);
        mutableChild.mergeWith(sibling.getTransientCopy());
        removeChild(pos);
        // change key for link to right
        setTransient(pos, mutableChild.getMinKey(), mutableChild);
      }
    }
    else if (childSize == 0) {
      removeChild(pos);
    }
    return true;
  }

  @Override
  public BaseTransientPage getTransientCopy() {
    throw new UnsupportedOperationException(); // TODO
  }

  @Override
  public IPage split(@NotNull Novelty.Accessor novelty, int from, int length) {
    final IPage result = copyOf(this, from, length);
    decrementSize(length);
    return result;
  }

  @Override
  @NotNull
  public IPage getChild(@NotNull Novelty.Accessor novelty, final int index) {
    final Object child = children[index];
    if (child instanceof BaseTransientPage) {
      return (BaseTransientPage)child;
    }
    return tree.storedTree.loadPage(novelty, (Address)child);
  }

  @Override
  public void insertDirectly(@NotNull Novelty.Accessor novelty, final int pos, @NotNull byte[] key, Object child) {
    if (pos < size) {
      copyChildren(pos, pos + 1);
    }
    final IPage page = (IPage)child;
    if (page.isTransient()) {
      setTransient(pos, key, page);
    }
    else {
      throw new IllegalArgumentException("non-transient child cannot be inserted here");
    }
    incrementSize();
    flush(novelty);
  }

  @Override
  public boolean isBottom() {
    return false;
  }

  @Override
  public IPage mergeWithChildren(@NotNull Novelty.Accessor novelty) {
    IPage result = this;
    while (!result.isBottom() && result.getSize() == 1) {
      result = ((IInternalPage)result).getChild(novelty, 0);
    }
    return result;
  }

  @Override
  protected void mergeWith(BaseTransientPage page) {
    System.arraycopy(((InternalTransientPage)page).children, 0, children, size, page.size);
    super.mergeWith(page);
  }

  private void removeChild(int pos) {
    copyChildren(pos + 1, pos);
    decrementSize(1);
  }

  private void setTransient(int pos, byte[] key, IPage child) {
    final int bytesPerKey = tree.getKeySize();
    final int offset = bytesPerKey * pos;

    // write key
    System.arraycopy(key, 0, backingArray, offset, bytesPerKey);

    // write value
    children[pos] = child;
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

    // copy children
    System.arraycopy(
      children, from,
      children, to,
      (size - from)
    );
  }

  private static InternalTransientPage copyOf(InternalTransientPage page, int from, int length) {
    byte[] bytes = new byte[page.backingArray.length];

    final int bytesPerKey = page.tree.getKeySize();

    // copy keys
    System.arraycopy(
      page.backingArray, from * bytesPerKey,
      bytes, 0,
      length * bytesPerKey
    );

    IPage[] children = new IPage[page.children.length];

    // copy children
    System.arraycopy(
      page.children, from,
      children, 0,
      length
    );

    return new InternalTransientPage(bytes, page.tree, length, children);
  }
}
