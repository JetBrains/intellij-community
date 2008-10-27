/*
 * @author max
 */
package com.intellij.util.indexing;

public interface CustomImplementationFileBasedIndexExtension<K, V, I> extends FileBasedIndexExtension<K, V> {
  UpdatableIndex<K, V, I> createIndexImplementation(final ID<K, V> indexId, final FileBasedIndex owner, final IndexStorage<K, V> storage);
}