// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package intellij.platform.onair.storage;

import intellij.platform.onair.storage.api.Address;
import intellij.platform.onair.storage.api.Storage;

public class DummyStorageImpl implements Storage {
  public static final Storage INSTANCE = new DummyStorageImpl();

  private DummyStorageImpl() {
  }

  @Override
  public byte[] lookup(Address address) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Address store(byte[] bytes) {
    throw new UnsupportedOperationException();
  }
}
