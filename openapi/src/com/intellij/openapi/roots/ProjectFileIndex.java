/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

public interface ProjectFileIndex extends FileIndex {
  /**
   * Returns module which content this file belongs to or null if it does not belong to content of any module.
   */
  Module getModuleForFile(VirtualFile file);

  /**
   * Returns order entries which contain a file (either in CLASSES or SOURCES)
   */
  OrderEntry[] getOrderEntriesForFile(VirtualFile file);

  /**
   * Returns a class root for a given file
   */
  VirtualFile getClassRootForFile(VirtualFile file);

  VirtualFile getSourceRootForFile(VirtualFile file);

  VirtualFile getContentRootForFile(VirtualFile file);

  String getPackageNameByDirectory(VirtualFile dir); //Q: move to FileIndex?

  /**
   * Returns true if <code>file</code> is a java source file which is treated as source (that is either project's source or library source)
   */
  boolean isJavaSourceFile(VirtualFile file);

  /**
   * Returns true if <code>file</code> is a compiled class file which belongs to some library
   */
  boolean isLibraryClassFile(VirtualFile file);

  /**
   * Returns true if <code>fileOrDir</code> is a file or directory from the content source or library sources
   */
  boolean isInSource(VirtualFile fileOrDir);

  /**
   * Returns true if <code>fileOrDir</code> is a file or directory from library classes
   */
  boolean isInLibraryClasses(VirtualFile fileOrDir);

  /**
   * Returns true if <code>fileOrDir</code> is a file or directory from library source
   */
  boolean isInLibrarySource(VirtualFile fileOrDir);

  /**
   * Returns true if <code>file</code> is file or directory which is ignored. That is, it is either excluded by exclude roots
   * or ignored by {@link com.intellij.openapi.fileTypes.FileTypeManager#isFileIgnored(String)}
   */
  boolean isIgnored(VirtualFile file);

}
