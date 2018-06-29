// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.tree;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import static intellij.platform.onair.tree.ByteUtils.normalizeLowBytes;

public class MockStorage implements Storage {
  private static final HashFunction HASH = Hashing.goodFastHash(128);

  public final ConcurrentHashMap<Address, byte[]> data = new ConcurrentHashMap<>();

  @Override
  public byte[] lookup(@NotNull Address address) {
    byte[] result = data.get(address);
    if (result == null) {
      throw new IllegalArgumentException("data missing");
    }
    return result;
  }

  @NotNull
  @Override
  public Address alloc(@NotNull byte[] bytes) {
    byte[] hashCode = HASH.hashBytes(bytes).asBytes();
    long lowBytes = ByteUtils.readUnsignedLong(hashCode, 0, 8);
    long highBytes = ByteUtils.readUnsignedLong(hashCode, 8, 8);
    return new Address(highBytes, normalizeLowBytes(lowBytes));
  }

  @Override
  public void store(@NotNull Address address, @NotNull byte[] bytes) {
    final Address result = alloc(bytes);
    final byte[] existing = data.putIfAbsent(result, bytes);
    if (existing != null) {
      if (!Arrays.equals(bytes, existing)) {
        throw new IllegalStateException("hash collision");
      }
    }
  }

  @Override
  public void prefetch(@NotNull byte[] bytes, @NotNull BTree tree, int size) {
  }
}
