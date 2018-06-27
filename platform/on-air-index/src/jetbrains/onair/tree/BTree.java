// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetbrains.onair.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class BTree {
    public static final byte BOTTOM = 4;
    public static final byte INTERNAL = 5;
    public static final byte LEAF = 6;

    private final Storage storage;
    private final int base = 32;
    private final int keySize;
    private final int addressSize;

    private long address;
    private final ConcurrentHashMap<Long, byte[]> novelty = new ConcurrentHashMap<>();

    private final AtomicLong addressGenerator = new AtomicLong(-2);

    public BTree(Storage storage, int keySize, int addressSize, long address) {
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

    // TODO: move to novelty
    public long alloc(byte[] bytes) {
        long result = addressGenerator.getAndDecrement();
        novelty.put(result, bytes);
        return result;
    }

    protected void incrementSize() {
        // TODO
    }

    @Nullable
    protected byte[] get(@NotNull byte[] key) {
        return loadPage(address).get(key);
    }

    public boolean put(@NotNull byte[] key, @NotNull byte[] value) {
        return put(key, value, true);
    }

    public boolean put(@NotNull byte[] key, @NotNull byte[] value, boolean overwrite) {
        final boolean[] result = new boolean[1];
        final BasePage rootPage = loadPage(address).getMutableCopy(this);
        final BasePage page = rootPage.put(key, value, overwrite, result);
        address = (page == null ? rootPage : page).address;
        return result[0];
    }

    public BasePage loadPage(long address) {
        byte[] bytes = address < 0 ? novelty.get(address) : storage.lookup(address);
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

    public byte[] loadLeaf(long childAddress) {
        return address < 0 ? novelty.get(childAddress) : storage.lookup(childAddress);
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
