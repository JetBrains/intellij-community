// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;

public class BTree implements Tree {
  public static final byte DEFAULT_BASE = 32;

  public static final byte BOTTOM = 4;
  public static final byte INTERNAL = 5;
  // public static final byte LEAF = 6;

  public static final int BYTES_PER_ADDRESS = 16;

  private final Storage storage;
  // private final int base = 32;
  private final int keySize;

  private Address address;

  private BTree(Storage storage, int keySize, Address address) {
    this.storage = storage;
    this.keySize = keySize;
    this.address = address;
  }

  @Override
  public int getKeySize() {
    return keySize;
  }

  @Override
  public int getBase() {
    return DEFAULT_BASE;
  }

  protected void incrementSize() {
  }

  protected void decrementSize() {
  }

  @Override
  @Nullable
  public byte[] get(@NotNull Novelty novelty, @NotNull byte[] key) {
    return loadPage(novelty, address).get(novelty, key);
  }

  @Override
  public boolean forEach(@NotNull Novelty novelty, @NotNull KeyValueConsumer consumer) {
    return loadPage(novelty, address).forEach(novelty, consumer);
  }

  @Override
  public boolean put(@NotNull Novelty novelty, @NotNull byte[] key, @NotNull byte[] value) {
    return put(novelty, key, value, true);
  }

  @Override
  public boolean put(@NotNull Novelty novelty, @NotNull byte[] key, @NotNull byte[] value, boolean overwrite) {
    final boolean[] result = new boolean[1];
    final BasePage root = loadPage(novelty, address).getMutableCopy(novelty, this);
    final BasePage newSibling = root.put(novelty, key, value, overwrite, result);
    if (newSibling != null) {
      final int metadataOffset = (keySize + BYTES_PER_ADDRESS) * DEFAULT_BASE;
      final byte[] bytes = new byte[metadataOffset + 2];
      bytes[metadataOffset] = INTERNAL;
      bytes[metadataOffset + 1] = 2;
      BasePage.set(0, root.getMinKey(), getKeySize(), bytes, root.address.getLowBytes());
      BasePage.set(1, newSibling.getMinKey(), getKeySize(), bytes, newSibling.address.getLowBytes());
      this.address = new Address(novelty.alloc(bytes));
    }
    else {
      this.address = root.address;
    }
    return result[0];
  }

  @Override
  public boolean delete(@NotNull Novelty novelty, @NotNull byte[] key) {
    return delete(novelty, key, null);
  }

  @Override
  public boolean delete(@NotNull Novelty novelty, @NotNull byte[] key, @Nullable byte[] value) {
    final boolean[] res = new boolean[1];
    address = delete(novelty, loadPage(novelty, address).getMutableCopy(novelty, this), key, value, res).address;
    return res[0];
  }

  @Override
  public Address store(@NotNull Novelty novelty) {
    return store(novelty, storage);
  }

  @Override
  public Address store(@NotNull Novelty novelty, @NotNull StorageConsumer consumer) {
    Address result = loadPage(novelty, address).save(novelty, storage, consumer);
    System.out.println("tree stored at " + result);
    return result;
  }

  public void dump(@NotNull Novelty novelty, @NotNull PrintStream out, BTree.ToString renderer) {
    loadPage(novelty, address).dump(novelty, out, 0, renderer);
  }

  /* package */ BasePage loadPage(@NotNull Novelty novelty, Address address) {
    final boolean isNovelty = address.isNovelty();
    final byte[] bytes = isNovelty ? novelty.lookup(address.getLowBytes()) : storage.lookup(address);
    if (bytes == null) {
      throw new IllegalStateException("page not found at " + address);
    }
    int metadataOffset = (keySize + BYTES_PER_ADDRESS) * getBase();
    byte type = bytes[metadataOffset];
    int size = bytes[metadataOffset + 1] & 0xff; // 0..255
    final BasePage result;
    if (!isNovelty) {
      storage.prefetch(address, bytes, this, size, type);
    }
    switch (type) {
      case BOTTOM:
        result = new BottomPage(bytes, this, address, size);
        break;
      case INTERNAL:
        result = new InternalPage(bytes, this, address, size);
        break;
      default:
        throw new IllegalArgumentException("Unknown page type [" + type + ']');
    }
    return result;
  }

  /* package */ byte[] loadLeaf(@NotNull Novelty novelty, Address childAddress) {
    return childAddress.isNovelty() ? novelty.lookup(childAddress.getLowBytes()) : storage.lookup(childAddress);
  }

  public static BTree load(@NotNull Storage storage, int keySize, Address address) {
    return new BTree(storage, keySize, address);
  }

  public static BTree create(@NotNull Novelty novelty, @NotNull Storage storage, int keySize) {
    final int metadataOffset = (keySize + BYTES_PER_ADDRESS) * DEFAULT_BASE;
    final byte[] bytes = new byte[metadataOffset + 2];
    bytes[metadataOffset] = BOTTOM;
    bytes[metadataOffset + 1] = 0;
    return new BTree(storage, keySize, new Address(novelty.alloc(bytes)));
  }

  private static BasePage delete(@NotNull Novelty novelty,
                                 @NotNull BasePage root,
                                 @NotNull byte[] key,
                                 @Nullable byte[] value,
                                 boolean[] res) {
    if (root.delete(novelty, key, value)) {
      root = root.mergeWithChildren(novelty);
      res[0] = true;
      return root;
    }

    res[0] = false;
    return root;
  }

  public interface ToString {

    String renderKey(byte[] key);

    String renderValue(byte[] value);
  }
}
