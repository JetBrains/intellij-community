/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VirtualFile;

/**
 *  @author dsl
 */
public interface ContentEntry extends Synthetic {
  /**
   * Returns root of this content, if it is valid.
   * @return null if content entry is invalid
   */
  VirtualFile getFile();

  /**
   * Returns URL of content root.
   * To validate returned roots,use
   * <code>{@link com.intellij.openapi.vfs.VirtualFileManager#findFileByUrl(java.lang.String)}</code>
   * @return URL of content root, that should never be null.
   */
  String getUrl();

  /**
   * @return list of this <code>ContentEntry</code> {@link com.intellij.openapi.roots.SourceFolder}s
   */
  SourceFolder[] getSourceFolders();

  /**
   * @return list of all valid source roots.
   */
  VirtualFile[] getSourceFolderFiles();


  /**
   * @return list of this <code>ContentEntry</code> {@link com.intellij.openapi.roots.ExcludeFolder}s
   */
  ExcludeFolder[] getExcludeFolders();

  /**
   * @return list of all valid exclude roots.
   */
  VirtualFile[] getExcludeFolderFiles();

  SourceFolder addSourceFolder(VirtualFile file, boolean isTestSource);
  void removeSourceFolder(SourceFolder sourceFolder);

  ExcludeFolder addExcludeFolder(VirtualFile file);
  void removeExcludeFolder(ExcludeFolder excludeFolder);

  SourceFolder addSourceFolder(VirtualFile file, boolean isTestSource, String packagePrefix);
}
