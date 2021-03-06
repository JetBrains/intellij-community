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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Base implementation for <a href="https://en.wikipedia.org/wiki/Search_engine_indexing#The_forward_index">forward indices</a>
 * that produce single value per single file.
 * <p>
 * Can be used to cache heavy computable file's data while the IDE is indexing.
 */
@ApiStatus.OverrideOnly
public abstract class SingleEntryFileBasedIndexExtension<V> extends FileBasedIndexExtension<Integer, V>{
  @NotNull
  @Override
  public final KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getCacheSize() {
    return 5;
  }

  @NotNull
  @Override
  public abstract SingleEntryIndexer<V> getIndexer();

  @Override
  public boolean keyIsUniqueForIndexedFile() {
    return true;
  }

  /**
   * @deprecated
   *
   * Should not be used because "index key" (namely, file id) should be not directly accessed in case of {@link SingleEntryFileBasedIndexExtension}.
   * Use {@link FileBasedIndex#getFileData(ID, VirtualFile, Project)} instead for index queries.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public static int getFileKey(@NotNull VirtualFile file) {
    return Math.abs(FileBasedIndex.getFileId(file));
  }
}
