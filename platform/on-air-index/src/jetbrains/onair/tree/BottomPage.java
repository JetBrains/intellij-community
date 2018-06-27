// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package jetbrains.onair.tree;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class BottomPage extends BasePage {

    public BottomPage(byte[] backingArray, BTree tree, long address, int size) {
        super(backingArray, tree, address, size);
    }

    @Nullable
    @Override
    protected byte[] get(@NotNull byte[] key) {
        final int index = binarySearch(key, 0);
        if (index >= 0) {
            return tree.loadLeaf(getChildAddress(index));
        }
        return null;
    }

    @Override
    protected BasePage getChild(int index) {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    protected BasePage put(@NotNull byte[] key, @NotNull byte[] value, boolean overwrite, boolean[] result) {
        int pos = binarySearch(key, 0);
        if (pos >= 0) {
            if (overwrite) {
                // key found
                // TODO: tree.addExpired(keysAddresses[pos]);
                set(pos, key, tree.alloc(Arrays.copyOf(value, value.length)));
                // this should be always true in order to keep up with keysAddresses[pos] expiration
                result[0] = true;
            }
            return null;
        }

        // if found - insert at this position, else insert after found
        pos = -pos - 1;

        final BasePage page = insertAt(pos, key, tree.alloc(Arrays.copyOf(value, value.length)));
        result[0] = true;
        tree.incrementSize();
        return page;
    }

    @Override
    protected BottomPage split(int from, int length) {
        final BottomPage result = BottomPage.copyOf(this, from, length);
        decrementSize(length);
        return result;
    }

    @Override
    protected BottomPage getMutableCopy(BTree tree) {
        if (address < 0) {
            return this;
        }
        byte[] bytes = Arrays.copyOf(this.backingArray, backingArray.length);
        return new BottomPage(
                bytes,
                tree, tree.alloc(bytes), size
        );
    }

    private static BottomPage copyOf(BottomPage page, int from, int length) {
        byte[] bytes = new byte[page.backingArray.length];

        final int bytesPerEntry = page.tree.getKeySize() + page.tree.getAddressSize();

        System.arraycopy(
                page.backingArray, from * bytesPerEntry,
                bytes, 0,
                length * bytesPerEntry
        );

        final int metadataOffset = bytesPerEntry * page.tree.getBase();

        bytes[metadataOffset] = BTree.BOTTOM;
        bytes[metadataOffset + 1] = (byte) length;

        return new BottomPage(bytes, page.tree, page.tree.alloc(bytes), length);
    }
}
