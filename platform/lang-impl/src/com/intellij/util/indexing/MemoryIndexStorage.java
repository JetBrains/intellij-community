/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.util.indexing;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * This storage is needed for indexing yet unsaved data without saving those changes to 'main' backend storage
 *
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public class MemoryIndexStorage<Key, Value> extends MemoryIndexStorageBase<Key, Value, VfsAwareIndexStorage<Key, Value>> implements VfsAwareIndexStorage<Key, Value> {
  public MemoryIndexStorage(@NotNull VfsAwareIndexStorage<Key, Value> backend, ID<?, ?> indexId) {
    super(backend, indexId);
  }

  @Override
  public boolean processKeys(@NotNull final Processor<Key> processor, GlobalSearchScope scope, IdFilter idFilter) throws StorageException {
    final Processor<Key> uniqueResultProcessor = processCacheFirstProcessor(processor);
    return uniqueResultProcessor != null && myBackendStorage.processKeys(uniqueResultProcessor, scope, idFilter);
  }
}
