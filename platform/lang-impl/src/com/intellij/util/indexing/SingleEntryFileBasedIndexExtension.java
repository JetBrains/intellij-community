package com.intellij.util.indexing;

import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;

/**
 * Base implementation for indices that produce single value per single file
 *
 * @author Eugene Zhuravlev
 *         Date: Feb 18, 2009
 */
public abstract class SingleEntryFileBasedIndexExtension<V> extends FileBasedIndexExtension<Integer, V>{
  public final KeyDescriptor<Integer> getKeyDescriptor() {
    return new EnumeratorIntegerDescriptor();
  }

  public boolean dependsOnFileContent() {
    return true;
  }

  public abstract SingleEntryIndexer<V> getIndexer();
}
