// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.hints.FileTypeIndexingHint;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * {@link IdIndex} is used in the reference searcher and other API that require "plain text" search by the identifier.
 * Please note that for "find in path" search usually  {@link com.intellij.find.ngrams.TrigramIndex} is used instead
 * <p>
 * Implementation _could_ implement {@link FileTypeIndexingHint} to customize input files filtering -- i.e. eagerly filter
 * out files that should not be indexed.
 * If implementation does not implement {@link FileTypeIndexingHint} then all the files of the type this indexer is registered
 * for -- will be indexed.
 *
 * @author traff
 * @see IdIndex
 */
@ApiStatus.OverrideOnly
public interface IdIndexer extends DataIndexer<IdIndexEntry, Integer, FileContent> {
  /**
   * @return Map[IdIndexEntry -> {@link com.intellij.psi.search.UsageSearchContext}] (see {@link IdIndex} for the meaning)
   * <b>BEWARE</b>: Even though this method declares generic {@link Map} return value, it is <b>highly recommended</b> to
   * use {@link IdDataConsumer} helper to collect (ID, occurenceMask) pairs in this method implementation, instead of generic
   * {@link Map}.
   * The {@link IdDataConsumer} helper returns specialized Map implementation, much better in memory-consumption and serialization
   * (and an actual implementation could be improved even more later).
   * It is not strictly required -- everything will work with any Map impl -- but you'll get a periodical warning in logs about
   * inoptimal use.
   */
  @Override
  @NotNull Map<IdIndexEntry, Integer> map(@NotNull FileContent inputData);

  default int getVersion() {
    return 1;
  }
}
