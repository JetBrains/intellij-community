/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;

public interface FileIndex {
  /**
   * Iterates all files and directories in the content.
   * @return false if files processing was stopped ({@link ContentIterator#processFile(VirtualFile)} returned false)
   */
  boolean iterateContent(ContentIterator iterator);

  /**
   * Iterates all files and directories in the content under directory <code>dir</code> (including the directory itself).
   * Does not iterate anything if <code>dir</code> is not in the content.
   * @return false if files processing was stopped ({@link ContentIterator#processFile(VirtualFile)} returned false)
   */
  boolean iterateContentUnderDirectory(VirtualFile dir, ContentIterator iterator);

  /**
   * Returns true if <code>fileOrDir</code> is a file or directory from the content
   */
  boolean isInContent(VirtualFile fileOrDir);

  /**
   * Returns true if <code>file</code> is a java source file which belongs to sources of the content.
   * Note that sometimes a java file can belong to the content and be a source file but not belong to sources of the content.
   * This happens if sources of some library are located under the content (so they belong to the project content but not as sources).
   */
  boolean isContentJavaSourceFile(VirtualFile file);

  /**
   * Returns true if <code>fileOrDir</code> is a file or directory from the content source.
   * (Returns true for both source and test source).
   */
  boolean isInSourceContent(VirtualFile fileOrDir);

  /**
   * Returns true if <code>fileOrDir</code> is a file or directory from the test content source
   */
  boolean isInTestSourceContent(VirtualFile fileOrDir);

  /**
   * Returns all directories in content sources and libraries (but not library sources) corresponding to the given package name.
   */
  VirtualFile[] getDirectoriesByPackageName(String packageName, boolean includeLibrarySources);
}
