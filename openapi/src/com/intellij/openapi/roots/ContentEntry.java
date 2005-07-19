/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
