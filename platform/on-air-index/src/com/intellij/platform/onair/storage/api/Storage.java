// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage.api;

import com.intellij.platform.onair.tree.BTree;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Storage {

  Storage VOID = new Storage() {
    @Override
    @NotNull
    public byte[] lookup(@NotNull Address address) {
      return new byte[0];
    }

    @Override
    public void bulkLookup(@NotNull List<Address> addresses, @NotNull DataConsumer consumer) {
    }

    @Override
    @NotNull
    public Address alloc(@NotNull byte[] what) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void prefetch(@NotNull Address address, @NotNull byte[] bytes, @NotNull BTree tree, int size, byte type, int mask) {
    }

    @Override
    public Address bulkStore(@NotNull Tree tree, @NotNull Novelty.Accessor novelty) {
      throw new UnsupportedOperationException();
    }
  };

  Address bulkStore(@NotNull Tree tree, @NotNull Novelty.Accessor novelty);

  @NotNull
  byte[] lookup(@NotNull Address address);

  void bulkLookup(@NotNull List<Address> addresses, @NotNull DataConsumer consumer);

  @NotNull
  Address alloc(@NotNull byte[] what);

  void prefetch(@NotNull Address address, @NotNull byte[] bytes, @NotNull BTree tree, int size, byte type, int mask);

  interface DataConsumer {
    void consume(Address address, byte[] value);
  }
}
