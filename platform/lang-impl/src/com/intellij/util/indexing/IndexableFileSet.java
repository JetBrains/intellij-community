/*
 * @author max
 */
package com.intellij.util.indexing;

import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFile;

public interface IndexableFileSet {
  boolean isInSet(VirtualFile file);
  void iterateIndexableFilesIn(VirtualFile file, ContentIterator iterator);
}