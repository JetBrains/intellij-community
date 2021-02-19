// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.storage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.indexing.FileBasedIndexExtension;
import org.jetbrains.annotations.NotNull;

/**
 * A main interface to override index storages.
 * Use {@link FileBasedIndexLayoutProviderBean} to register a plugin which could provide custom index storage.
 */
public interface FileBasedIndexLayoutProvider {
  ExtensionPointName<FileBasedIndexLayoutProviderBean> STORAGE_LAYOUT_EP_NAME
    = ExtensionPointName.create("com.intellij.fileBasedIndexLayout");

  /**
   * @return storages required to realize IJ file-based indexes.
   */
  @NotNull
  <K, V> VfsAwareIndexStorageLayout<K, V> getLayout(@NotNull FileBasedIndexExtension<K, V> extension);
}
