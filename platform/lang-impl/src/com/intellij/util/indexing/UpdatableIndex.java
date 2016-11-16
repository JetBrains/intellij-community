/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 10, 2007
 */
public interface UpdatableIndex<Key, Value, Input> extends AbstractUpdatableIndex<Key,Value, Input> {
  boolean processAllKeys(@NotNull Processor<Key> processor, GlobalSearchScope scope, @Nullable IdFilter idFilter) throws StorageException;

  void setIndexedStateForFile(int fileId, @NotNull VirtualFile file);
  void resetIndexedStateForFile(int fileId);

  boolean isIndexedStateForFile(int fileId, @NotNull VirtualFile file);

  @Override
  default boolean processAllKeys(@NotNull Processor<Key> processor) throws StorageException {
    return processAllKeys(processor, null, null);
  }

  @NotNull
  @Override
  default Class<? extends RuntimeException> getProcessCanceledExceptionClass() {
    return ProcessCanceledException.class;
  }
}
