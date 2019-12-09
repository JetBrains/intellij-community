// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;


public interface CompositeDataIndexer<K, V, SubIndexerType, SubIndexerVersion> extends DataIndexer<K, V, FileContent> {
  Key<?> SUB_INDEXER_KEY = Key.create("intellij.sub.indexer.type");
  boolean IS_ENABLED = SystemProperties.is("com.intellij.composite.indexers");

  /**
   * return all available sub-indexers. Used very rarely on FileBasedIndex startup
   */
  @NotNull
  Collection<SubIndexerType> getAllAvailableSubIndexers();

  @Nullable
  SubIndexerType calculateSubIndexer(@NotNull VirtualFile content);

  /**
   * determine should we load content to provide sub-indexer
   */
  boolean requiresContentForSubIndexerEvaluation(@NotNull VirtualFile content);

  /**
   * Note that sub-indexer serialization algorithm should not depend on the current session or other things.
   * For example you must not save any unsorted data (ex: HashSet-s) here.
   */
  @NotNull
  SubIndexerVersion getSubIndexerVersion(@NotNull SubIndexerType subIndexerType);

  @NotNull
  KeyDescriptor<SubIndexerVersion> getSubIndexerVersionDescriptor();

  @NotNull
  @Override
  default Map<K, V> map(@NotNull FileContent inputData) {
    SubIndexerType subIndexerType = (SubIndexerType)SUB_INDEXER_KEY.get(inputData);
    if (subIndexerType == null && !IS_ENABLED) {
      subIndexerType = calculateSubIndexer(inputData.getFile());
    }
    if (subIndexerType == null) return Collections.emptyMap();
    return map(inputData, subIndexerType);
  }

  @NotNull
  Map<K, V> map(@NotNull FileContent inputData, @NotNull SubIndexerType indexerType);
}
