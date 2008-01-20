package com.intellij.psi.impl.cache.index;

import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.PersistentEnumerator;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 20, 2008
 */
public class TodoIndex implements FileBasedIndexExtension<TodoIndexEntry, Integer> {

  public int getVersion() {
    return 1;
  }

  public String getName() {
    return null;
  }

  public DataIndexer<TodoIndexEntry, Integer, FileBasedIndex.FileContent> getIndexer() {
    return null;
  }

  public PersistentEnumerator.DataDescriptor<TodoIndexEntry> getKeyDescriptor() {
    return null;
  }

  public DataExternalizer<Integer> getValueExternalizer() {
    return null;
  }

  public FileBasedIndex.InputFilter getInputFilter() {
    return null;
  }

}
