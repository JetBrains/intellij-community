// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Storage;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public class MockStorage implements Storage {
  private static final HashFunction HASH = Hashing.goodFastHash(128);

  public final ConcurrentHashMap<Address, byte[]> data = new ConcurrentHashMap<>();

  public byte[] lookup(Address address) {
    byte[] result = data.get(address);
    if (result == null) {
      throw new IllegalArgumentException("data missing");
    }
    return result;
  }

  public Address store(byte[] bytes) {
    byte[] hashCode = HASH.hashBytes(bytes).asBytes();
    long lowBytes = ByteUtils.readUnsignedLong(hashCode, 0, 8);
    long highBytes = ByteUtils.readUnsignedLong(hashCode, 0, 8);
    final Address result = new Address(highBytes, normalizeLowBytes(lowBytes));
    final byte[] existing = data.putIfAbsent(result, bytes);
    if (existing != null) {
      if (!Arrays.equals(bytes, existing)) {
        throw new IllegalStateException("hash collision");
      }
    }
    return result;
  }

  private static long normalizeLowBytes(long address) {
    if (address < 0) {
      return address;
    }
    if (address == 0) {
      return Long.MIN_VALUE;
    }
    return -address;
  }
}
