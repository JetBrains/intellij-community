/*
 * @author max
 */
package com.intellij.util.indexing;

public abstract class CustomImplementationFileBasedIndexExtension<K, V, I> extends FileBasedIndexExtension<K, V> {
  public abstract UpdatableIndex<K, V, I> createIndexImplementation(final ID<K, V> indexId, final FileBasedIndex owner, final IndexStorage<K, V> storage);
}