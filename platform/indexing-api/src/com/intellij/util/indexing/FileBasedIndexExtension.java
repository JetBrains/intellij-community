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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 * V class MUST have equals / hashcode properly defined!!!
 */
public abstract class FileBasedIndexExtension<K, V> extends IndexExtension<K, V, FileContent>{
  public static final ExtensionPointName<FileBasedIndexExtension> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.fileBasedIndex");
  public static final int DEFAULT_CACHE_SIZE = 1024;

  @NotNull
  @Override
  public abstract ID<K, V> getName();

  @NotNull
  public abstract FileBasedIndex.InputFilter getInputFilter();
  
  public abstract boolean dependsOnFileContent();

  public boolean indexDirectories() {
    return false;
  }

  /**
   * @see FileBasedIndexExtension#DEFAULT_CACHE_SIZE
   */
  public int getCacheSize() {
    return DEFAULT_CACHE_SIZE;
  }

  /**
   * For most indices the method should return an empty collection.
   * @return collection of file types to which file size limit will not be applied when indexing.
   * This is the way to allow indexing of files whose limit exceeds FileManagerImpl.MAX_INTELLISENSE_FILESIZE.
   *
   * Use carefully, because indexing large files may influence index update speed dramatically.
   *
   * @see com.intellij.openapi.vfs.PersistentFSConstants#getMaxIntellisenseFileSize()
   */
  @NotNull
  public Collection<FileType> getFileTypesWithSizeLimitNotApplicable() {
    return Collections.emptyList();
  }

  public boolean keyIsUniqueForIndexedFile() {
    return false;
  }

  public boolean traceKeyHashToVirtualFileMapping() {
    return false;
  }

  public boolean hasSnapshotMapping() {
    return false;
  }
}
