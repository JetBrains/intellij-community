// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Represents {@link DataIndexer} which behaviour can be extended by some kind of extension points.
 * <p>
 * See {@link com.intellij.psi.impl.cache.impl.id.IdIndex}, {@link com.intellij.psi.stubs.StubUpdatingIndex} as examples.
 * </p>
 */
@ApiStatus.Experimental
public interface CompositeDataIndexer<K, V, SubIndexerType, SubIndexerVersion> extends DataIndexer<K, V, FileContent> {
  /**
   * Calculates sub-indexer type which will be used by indexing algorithm.
   * Usually SubIndexerType it's some extension which build index for a given file.
   *
   * @return null if file is not acceptable for indexing
   * @see CompositeDataIndexer#map(FileContent, Object)
   */
  @Nullable
  SubIndexerType calculateSubIndexer(@NotNull IndexedFile file);

  /**
   * Determine should we load content to provide sub-indexer.
   */
  default boolean requiresContentForSubIndexerEvaluation(@NotNull IndexedFile file) {
    return false;
  }

  /**
   * SubIndexerVersion reflects StubIndexerType persistent version.
   * It should depend only on it and must not use any additional information about IDE setup.
   */
  @NotNull
  SubIndexerVersion getSubIndexerVersion(@NotNull SubIndexerType subIndexerType);

  /**
   * SubIndexerVersion descriptor must depend only on corresponding index version, should be available to read even corresponding SubIndexerType is not exist anymore.
   */
  @NotNull
  KeyDescriptor<SubIndexerVersion> getSubIndexerVersionDescriptor();

  @NotNull
  @Override
  default Map<K, V> map(@NotNull FileContent inputData) {
    SubIndexerType subIndexerType = calculateSubIndexer(inputData);
    return subIndexerType == null ? Collections.emptyMap() : map(inputData, Objects.requireNonNull(subIndexerType));
  }

  /**
   * @return indexed data for an input provided by indexerType argument
   */
  @NotNull
  Map<K, V> map(@NotNull FileContent inputData, @NotNull SubIndexerType indexerType);
}
