/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.openapi.vfs.VirtualFile;

public interface CustomImplementationFileBasedIndexExtension<K, V, I> extends FileBasedIndexExtension<K, V> {
  int perFileVersion(VirtualFile file);
  UpdatableIndex<K, V, I> createIndexImplementation(final FileBasedIndex owner, final IndexStorage<K, V> storage);
}