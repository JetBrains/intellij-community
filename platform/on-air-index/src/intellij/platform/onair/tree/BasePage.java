// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BasePage {
    protected final byte[] backingArray;
    protected final BTree tree;
    protected final long address;

    protected int size;

    public BasePage(byte[] backingArray, BTree tree, long address, int size) {
        this.backingArray = backingArray;
        this.tree = tree;
        this.address = address;
        this.size = size;
    }

    @Nullable
    protected abstract byte[] get(@NotNull final byte[] key);

    protected abstract BasePage getChild(int index);

    @Nullable
    protected abstract BasePage put(@NotNull byte[] key, @NotNull byte[] value, boolean overwrite, boolean[] result);

    protected abstract BasePage getMutableCopy(BTree tree);

    protected long getChildAddress(int index) {
        final int bytesPerKey = tree.getKeySize();
        final int bytesPerValue = tree.getAddressSize();
        final int offset = (bytesPerKey + bytesPerValue) * index + bytesPerKey;

        return readUnsignedLong(backingArray, offset, bytesPerValue);
    }

    protected byte[] getKey(int index) {
        return tree.loadLeaf(getChildAddress(index));
    }

    protected abstract BasePage split(int from, int length);

    protected void incrementSize() {
        if (size >= tree.getBase()) {
            throw new IllegalArgumentException("Can't increase tree page size");
        }
        setSize(size + 1);
    }

    protected void decrementSize(final int value) {
        if (size < value) {
            throw new IllegalArgumentException("Can't decrease tree page size " + size + " on " + value);
        }
        setSize(size - value);
    }

    // TODO: consider caching minKey like xodus BasePageImmutable does
    @NotNull byte[] getMinKey() {
        if (size <= 0) {
            throw new ArrayIndexOutOfBoundsException("Page is empty.");
        }

        return getKey(0);
    }

    protected int binarySearch(byte[] key, int low) {
        final int bytesPerKey = tree.getKeySize();
        final int bytesPerEntry = bytesPerKey + tree.getAddressSize();

        int high = size - 1;

        while (low <= high) {
            final int mid = (low + high) >>> 1;
            final int offset = mid * bytesPerEntry;

            final int cmp = compare(backingArray, bytesPerKey, offset, key, key.length, 0);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                // key found
                return mid;
            }
        }
        // key not found
        return -(low + 1);
    }

    protected void set(int pos, byte[] key, long childAddress) {
        final int bytesPerKey = tree.getKeySize();
        final int bytesPerValue = tree.getAddressSize();

        if (key.length != bytesPerKey) {
            throw new IllegalArgumentException("Invalid key length: need " + bytesPerKey + ", got: " + key.length);
        }

        final int offset = (bytesPerKey + bytesPerValue) * pos;

        // write key
        System.arraycopy(key, 0, backingArray, offset, bytesPerKey);
        // write address
        writeUnsignedLong(childAddress, bytesPerValue, backingArray, offset + bytesPerKey);
    }

    protected BasePage insertAt(int pos, byte[] key, long childAddress) {
        if (!needSplit(this)) {
            insertDirectly(pos, key, childAddress);
            return null;
        } else {
            int splitPos = getSplitPos(this, pos);

            final BasePage sibling = split(splitPos, size - splitPos);
            if (pos >= splitPos) {
                // insert into right sibling
                sibling.insertAt(pos - splitPos, key, childAddress);
            } else {
                // insert into self
                insertAt(pos, key, childAddress);
            }
            return sibling;
        }
    }

    protected void insertDirectly(final int pos, @NotNull byte[] key, long childAddress) {
        if (pos < size) {
            copyChildren(pos, pos + 1);
        }
        incrementSize();
        set(pos, key, childAddress);
    }

    protected void copyChildren(final int from, final int to) {
        if (from >= size) return;

        final int bytesPerEntry = tree.getKeySize() + tree.getAddressSize();

        System.arraycopy(
                backingArray, from * bytesPerEntry,
                backingArray, to * bytesPerEntry,
                (size - from) * bytesPerEntry
        );
    }

    private void setSize(int updatedSize) {
        final int sizeOffset = ((tree.getKeySize() + tree.getAddressSize()) * tree.getBase()) + 1;
        backingArray[sizeOffset] = (byte) updatedSize;
        this.size = updatedSize;
    }

    public static int compare(@NotNull final byte[] key1, final int len1, final int offset1,
                              @NotNull final byte[] key2, final int len2, final int offset2) {
        final int min = Math.min(len1, len2);

        for (int i = 0; i < min; i++) {
            final byte b1 = key1[i + offset1];
            final byte b2 = key2[i + offset2];
            if (b1 != b2) {
                return (b1 & 0xff) - (b2 & 0xff);
            }
        }

        return len1 - len2;
    }

    // TODO: extract ByteUtils class
    public static long readUnsignedLong(@NotNull final byte[] bytes, final int offset, final int length) {
        long result = 0;
        for (int i = 0; i < length; ++i) {
            result = (result << 8) + ((int) bytes[offset + i] & 0xff);
        }
        return result;
    }

    // TODO: extract ByteUtils class
    public static void writeUnsignedLong(final long l,
                                         final int bytesPerLong,
                                         @NotNull final byte[] output,
                                         int offset) {
        int bits = bytesPerLong << 3;
        while (bits > 0) {
            output[offset++] = ((byte) (l >> (bits -= 8) & 0xff));
        }
    }

    // TODO: extract Policy class
    public boolean needSplit(@NotNull final BasePage page) {
        return page.size >= tree.getBase();
    }

    // TODO: extract Policy class
    public int getSplitPos(@NotNull final BasePage page, final int insertPosition) {
        // if inserting into the most right position - split as 8/1, otherwise - 1/1
        final int pageSize = page.size;
        return insertPosition < pageSize ? pageSize >> 1 : (pageSize * 7) >> 3;
    }

    // TODO: extract Policy class
    public boolean needMerge(@NotNull final BasePage left, @NotNull final BasePage right) {
        final int leftSize = left.size;
        final int rightSize = right.size;
        return leftSize == 0 || rightSize == 0 || leftSize + rightSize <= ((tree.getBase() * 7) >> 3);
    }
}
