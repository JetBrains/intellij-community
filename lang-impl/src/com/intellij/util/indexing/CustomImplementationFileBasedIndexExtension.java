/*
 * @author max
 */
package com.intellij.util.indexing;

public interface CustomImplementationFileBasedIndexExtension<K, V, I> extends FileBasedIndexExtension<K, V> {
  UpdatableIndex<K, V, I> createIndexImplementation(final IndexStorage<K, V> storage);
}