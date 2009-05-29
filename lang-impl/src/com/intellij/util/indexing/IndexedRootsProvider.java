package com.intellij.util.indexing;

import com.intellij.openapi.extensions.ExtensionPointName;

import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public interface IndexedRootsProvider {

  ExtensionPointName<IndexedRootsProvider> EP_NAME = new ExtensionPointName<IndexedRootsProvider>("com.intellij.indexedRootsProvider");

  /**
   * @return each string is VFS url {@link com.intellij.openapi.vfs.VirtualFile#getUrl()} of the root to index. Cannot depend on project.
   */
  Set<String> getRootsToIndex();
}
