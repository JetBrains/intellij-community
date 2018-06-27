// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class MockStorage implements Storage {
  private static final long MOCK_HIGH_BYTES = 0xCAFEBABE;

  public final ConcurrentHashMap<Address, byte[]> data = new ConcurrentHashMap<>();

  public byte[] lookup(Address address) {
    byte[] result = data.get(address);
    if (result == null) {
      throw new IllegalArgumentException("data missing");
    }
    return result;
  }

  public Address store(byte[] bytes) {
    final Address result = new Address(MOCK_HIGH_BYTES, getHash(bytes));
    final byte[] existing = data.putIfAbsent(result, bytes);
    if (existing != null) {
      if (!Arrays.equals(bytes, existing)) {
        throw new IllegalStateException("hash collision");
      }
    }
    return result;
  }

  private static long getHash(@NotNull byte[] what) {
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
