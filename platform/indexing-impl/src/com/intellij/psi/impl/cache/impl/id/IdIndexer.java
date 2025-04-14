// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.hints.FileTypeIndexingHint;
import org.jetbrains.annotations.ApiStatus;

/**
 * Id index is used in the reference searcher and other API that require "plain text" search by the identifier.
 * Please note that for "find in path" search usually  {@link com.intellij.find.ngrams.TrigramIndex} is used instead
 *
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
  default int getVersion() {
    return 1;
  }
}
