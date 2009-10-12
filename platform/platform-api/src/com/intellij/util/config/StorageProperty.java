/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.config;

import org.jetbrains.annotations.NonNls;

import java.util.Iterator;

public class StorageProperty extends AbstractProperty<Storage> {
  private final String myName;

  public StorageProperty(@NonNls String name) {
    myName = name;
  }

  public Storage getDefault(AbstractProperty.AbstractPropertyContainer container) {
    Storage.MapStorage storage = new Storage.MapStorage();
    set(container, storage);
    return storage;
  }

  public Storage copy(Storage storage) {
    if (!(storage instanceof Storage.MapStorage))
      throw new UnsupportedOperationException(storage.getClass().getName());
    Iterator<String> keys = ((Storage.MapStorage)storage).getKeys();
    Storage.MapStorage copy = new Storage.MapStorage();
    while (keys.hasNext()) {
      String key = keys.next();
      copy.put(key, storage.get(key));
    }
    return copy;
  }

  public String getName() {
    return myName;
  }
}
