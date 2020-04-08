// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;

/**
 * Id index is used in the reference searcher and other API that require "plain text" search by the identifier.
 * Please note that for "find in path" search usually  {@link com.intellij.find.ngrams.TrigramIndex} is used instead
 * 
 * @author traff
 * @see IdIndex
 */
public interface IdIndexer extends DataIndexer<IdIndexEntry, Integer, FileContent> {
  default int getVersion() {
    return 1;
  }
}
