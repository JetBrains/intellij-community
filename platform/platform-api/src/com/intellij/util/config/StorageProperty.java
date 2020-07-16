// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.config;

import org.jetbrains.annotations.NonNls;

import java.util.Iterator;

public final class StorageProperty extends AbstractProperty<Storage> {
  private final String myName;

  public StorageProperty(@NonNls String name) {
    myName = name;
  }

  @Override
  public Storage getDefault(AbstractProperty.AbstractPropertyContainer container) {
    Storage.MapStorage storage = new Storage.MapStorage();
    set(container, storage);
    return storage;
  }

  @Override
  public Storage copy(Storage storage) {
    if (!(storage instanceof Storage.MapStorage)) {
      throw new UnsupportedOperationException(storage.getClass().getName());
    }
    Iterator<String> keys = ((Storage.MapStorage)storage).getKeys();
    Storage.MapStorage copy = new Storage.MapStorage();
    while (keys.hasNext()) {
      String key = keys.next();
      copy.put(key, storage.get(key));
    }
    return copy;
  }

  @Override
  public String getName() {
    return myName;
  }
}
