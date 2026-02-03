// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.indexing.FileBasedIndexExtension;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

/**
 * A main interface to override index storages.
 * Use {@link FileBasedIndexLayoutProviderBean} to register a plugin which could provide custom index storage.
 * <p/>
 * <i>All</i> the providers registered are initialized during app startup, hence provider class instantiation should be
 * lightweight: any heavy-lifting, if needed, should be done lazily, in {@linkplain #getLayout(FileBasedIndexExtension, Iterable)}
 * call, not in a class constructor.
 * {@linkplain #isSupported()} method should check all the requirements for the current provider to work -- that implies
 * again, that constructor shouldn't access something that could fail (e.g. optional jni libs)
 */
@ApiStatus.Internal
public interface FileBasedIndexLayoutProvider {
  ExtensionPointName<FileBasedIndexLayoutProviderBean> STORAGE_LAYOUT_EP_NAME =
    ExtensionPointName.create("com.intellij.fileBasedIndexLayout");

  /**
   * @param otherApplicableProviders list (immutable) of other index storage providers that are <i>applicable</i> for the same extension,
   *                                 <i>ordered by priority</i>, descending -- i.e. the first item of the list is the next most preferable
   *                                 provider for the extension after the current one.
   *                                 Supplied for wrapping/delegating storage implementations, or to fall back to default provider,
   *                                 if some complex condition that can't be checked in {@linkplain #isApplicable(FileBasedIndexExtension)}
   *                                 is not met.
   *                                 The list could be empty.
   * @return storage layout to use for creating & managing extension's index
   */
  @NotNull <K, V> VfsAwareIndexStorageLayout<K, V> getLayout(
    @NotNull FileBasedIndexExtension<K, V> extension,
    @NotNull @Unmodifiable Iterable<? extends FileBasedIndexLayoutProvider> otherApplicableProviders
  );

  /** @return true if the provider is applicable <i>for the specific extension</i> -- i.e. is able to provide index storage layout for it */
  default boolean isApplicable(@NotNull FileBasedIndexExtension<?, ?> extension) {
    return true;
  }

  /** @return true if provider is supported by the current IDE/platform */
  default boolean isSupported() {
    return true;
  }
}
