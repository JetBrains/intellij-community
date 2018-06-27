// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;

public class Storage {
    public final ConcurrentHashMap<Long, byte[]> data = new ConcurrentHashMap<>();

    public Storage() {
    }

    byte[] lookup(long hash) {
        byte[] result = data.get(hash);
        if (result == null) {
            throw new IllegalArgumentException("data missing");
        }
        return result;
    }

    long store(byte[] what) {
        long hash = getHash(what);
        if (data.putIfAbsent(hash, what) != null) {
            throw new IllegalStateException();
        }
        return hash;
    }

    private long getHash(@NotNull byte[] what) {
        int result = 1;
        for (final byte element : what) {
            result = 31 * result + (element & 0xff);
        }

        if (result < 0) {
            throw new IllegalStateException();
        }

        return result;
    }
}
