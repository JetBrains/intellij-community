// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Novelty;
import intellij.platform.onair.storage.api.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BTree {
  public static final byte BOTTOM = 4;
  public static final byte INTERNAL = 5;
  public static final byte LEAF = 6;

  public static final int BYTES_PER_VALUE = 16;

  private final Storage storage;
  private final int base = 32;
  private final int keySize;
  private final int addressSize;

  private Address address;

  public BTree(Storage storage, int keySize, int addressSize, Address address) {
    this.storage = storage;
    this.keySize = keySize;
    this.addressSize = addressSize;
    this.address = address;
  }

  public int getKeySize() {
    return keySize;
  }

  public int getAddressSize() {
    return addressSize;
  }

  public int getBase() {
    return base;
  }

  protected void incrementSize() {
    // TODO
  }

  @Nullable
  protected byte[] get(@NotNull Novelty novelty, @NotNull byte[] key) {
    return loadPage(novelty, address).get(novelty, key);
  }

  public boolean put(@NotNull Novelty novelty, @NotNull byte[] key, @NotNull byte[] value) {
    return put(novelty, key, value, true);
  }

  public boolean put(@NotNull Novelty novelty, @NotNull byte[] key, @NotNull byte[] value, boolean overwrite) {
    final boolean[] result = new boolean[1];
    final BasePage rootPage = loadPage(novelty, address).getMutableCopy(novelty, this);
    final BasePage page = rootPage.put(novelty, key, value, overwrite, result);
    address = (page == null ? rootPage : page).address;
    return result[0];
  }

  public BasePage loadPage(@NotNull Novelty novelty, Address address) {
    byte[] bytes = address.isNovelty() ? novelty.lookup(address.getLowBytes()) : storage.lookup(address);
    int metadataOffset = (keySize + addressSize) * base;
    byte type = bytes[metadataOffset];
    byte size = bytes[metadataOffset + 1];
    final BasePage result;
    switch (type) {
      case BOTTOM:
        result = new BottomPage(bytes, this, address, size & 0xff); // 0..255
        break;
      case INTERNAL:
        result = new InternalPage(bytes, this, address, size & 0xff); // 0..255
        break;
      default:
        throw new IllegalArgumentException("Unknown page type [" + type + ']');
    }
    return result;
  }

  public byte[] loadLeaf(@NotNull Novelty novelty, Address childAddress) {
    return childAddress.isNovelty() ? novelty.lookup(childAddress.getLowBytes()) : storage.lookup(childAddress);
  }

  public static BTree createEmpty(Storage storage, int keySize, int addressSize) {
    final int metadataOffset = (keySize + addressSize) * 32;
    final byte[] bytes = new byte[metadataOffset + 2];
    bytes[metadataOffset] = BOTTOM;
    bytes[metadataOffset + 1] = 0;
    // TODO: use novelty
    return new BTree(storage, keySize, addressSize, storage.store(bytes));
  }
}
