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

package com.intellij.history.core;

import com.intellij.history.core.storage.BrokenStorageException;
import com.intellij.history.core.storage.Content;
import com.intellij.history.core.storage.Storage;
import com.intellij.history.core.storage.StoredContent;

import java.util.HashMap;
import java.util.Map;

public class InMemoryStorage extends Storage {
  private final Map<Integer, byte[]> myContents = new HashMap<Integer, byte[]>();

  public InMemoryStorage() {
    super(null);
  }

  @Override
  protected void initStorage() {
  }

  @Override
  public void saveContents() {
  }

  @Override
  public LocalVcs.Memento load() {
    return new LocalVcs.Memento();
  }

  @Override
  public void saveState(LocalVcs.Memento m) {
  }

  @Override
  public Content storeContent(byte[] bytes) {
    int id = myContents.size();
    myContents.put(id, bytes);
    return new StoredContent(this, id);
  }

  @Override
  protected byte[] loadContentData(int id) throws BrokenStorageException {
    return myContents.get(id);
  }

  @Override
  protected void purgeContent(StoredContent c) {
  }
}
