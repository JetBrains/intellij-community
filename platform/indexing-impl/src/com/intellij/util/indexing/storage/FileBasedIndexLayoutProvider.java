// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.indexing.FileBasedIndexExtension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A main interface to override index storages.
 * Use {@link FileBasedIndexLayoutProviderBean} to register a plugin which could provide custom index storage.
 */
@ApiStatus.Internal
public interface FileBasedIndexLayoutProvider {
  ExtensionPointName<FileBasedIndexLayoutProviderBean> STORAGE_LAYOUT_EP_NAME
    = ExtensionPointName.create("com.intellij.fileBasedIndexLayout");

  /**
   * @return storages required to realize IJ file-based indexes.
   */
  @NotNull
  <K, V> VfsAwareIndexStorageLayout<K, V> getLayout(@NotNull FileBasedIndexExtension<K, V> extension);

  /**
   * @return true if the provider is applicable for the specific extension -- i.e. is able to provide
   * index storage layout for it
   */
  default boolean isApplicable(@NotNull FileBasedIndexExtension<?, ?> extension) {
    return true;
  }

  /** @return true if provider is applicable for the IDE/platform */
  default boolean isSupported() {
    return true;
  }
}
