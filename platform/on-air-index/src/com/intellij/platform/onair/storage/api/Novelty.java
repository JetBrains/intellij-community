// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage.api;

import java.io.Closeable;

public interface Novelty extends Closeable {

  Accessor access();

  interface Accessor {
    // result must be less than 0
    long alloc(byte[] bytes);

    void free(long address);

    byte[] lookup(long address);

    void update(long address, byte[] bytes);
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  Novelty VOID = new Novelty() {
    @Override
    public Accessor access() {
      return VOID_TXN;
    }

    @Override
    public void close() {}
  };

  Accessor VOID_TXN = new Accessor() {
    @Override
    public long alloc(byte[] bytes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void free(long address) {
      throw new UnsupportedOperationException();
    }

    @Override
    public byte[] lookup(long address) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void update(long address, byte[] bytes) {
      throw new UnsupportedOperationException();
    }
  };
}
