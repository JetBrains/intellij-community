// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.intellij.platform.onair.storage.api.Address;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.storage.api.Storage;
import com.intellij.platform.onair.storage.api.Tree;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.platform.onair.tree.ByteUtils.normalizeLowBytes;

public class MockStorage implements Storage {
  private static final HashFunction HASH = Hashing.goodFastHash(128);

  public final ConcurrentHashMap<Address, byte[]> data = new ConcurrentHashMap<>();

  @NotNull
  @Override
  public byte[] lookup(@NotNull Address address) {
    byte[] result = data.get(address);
    if (result == null) {
      throw new NoSuchElementException("data missing");
    }
    return result;
  }

  @Override
  public void bulkLookup(@NotNull List<Address> addresses, @NotNull DataConsumer consumer) {
    addresses.forEach(address -> consumer.consume(address, lookup(address)));
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
  public Address bulkStore(@NotNull Tree tree, @NotNull Novelty.Accessor novelty) {
    return tree.store(novelty, (address, bytes) -> {
      final Address result = alloc(bytes);
      final byte[] existing = data.putIfAbsent(result, bytes);
      if (existing != null) {
        if (!Arrays.equals(bytes, existing)) {
          throw new IllegalStateException("hash collision");
        }
      }
    });
  }

  @Override
  public void prefetch(@NotNull Address address, @NotNull byte[] bytes, @NotNull BTree tree, int size, byte type, int mask) {
  }
}
