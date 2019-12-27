// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.util.ObjectUtils;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;


public interface CompositeDataIndexer<K, V, SubIndexerType, SubIndexerVersion> extends DataIndexer<K, V, FileContent> {
  /**
   * @return null if file is not acceptable for indexing
   */
  @Nullable
  SubIndexerType calculateSubIndexer(@NotNull IndexedFile file);

  /**
   * determine should we load content to provide sub-indexer
   */
  default boolean requiresContentForSubIndexerEvaluation(@NotNull IndexedFile file) {
    return false;
  }

  /**
   * Note that sub-indexer serialization algorithm should not depend on the current session or other things.
   * For example you must not save any unsorted data (ex: HashSet-s) here.
   */
  @NotNull
  SubIndexerVersion getSubIndexerVersion(@NotNull SubIndexerType subIndexerType);

  /**
   * SubIndexerVersion descriptor must depend only on corresponding index version, should be read even SubIndexerType is not available anymore
   */
  @NotNull
  KeyDescriptor<SubIndexerVersion> getSubIndexerVersionDescriptor();

  @NotNull
  @Override
  default Map<K, V> map(@NotNull FileContent inputData) {
    SubIndexerType subIndexerType = calculateSubIndexer(inputData);
    if (subIndexerType == null && !InvertedIndex.ARE_COMPOSITE_INDEXERS_ENABLED) {
      return Collections.emptyMap();
    }
    return map(inputData, ObjectUtils.notNull(subIndexerType));
  }

  @NotNull
  Map<K, V> map(@NotNull FileContent inputData, @NotNull SubIndexerType indexerType);
}
