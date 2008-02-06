package com.intellij.psi.impl.cache.index;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 16, 2008
 */
public abstract class FileTypeIdIndexer implements DataIndexer<IdIndexEntry, Integer, FileBasedIndex.FileContent> {
}
