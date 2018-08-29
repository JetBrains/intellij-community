// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage.api;

import java.io.Closeable;
import java.io.IOException;

public interface Novelty extends Closeable {

  // result must be less than 0
  long alloc(byte[] bytes);

  void free(long address);

  byte[] lookup(long address);

  void update(long address, byte[] bytes);

  Novelty unsynchronizedCopy();

  Novelty VOID = new Novelty() {
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

    @Override
    public Novelty unsynchronizedCopy() {
      return this;
    }

    @Override
    public void close() throws IOException {}
  };
}
