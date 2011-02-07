/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a module content root.
 * You can get existing entries with {@link com.intellij.openapi.roots.ModuleRootModel#getContentEntries()} or
 * create a new one with {@link ModifiableRootModel#addContentEntry(com.intellij.openapi.vfs.VirtualFile)}.
 *
 * @author dsl
 * @see ModuleRootModel#getContentEntries()
 * @see ModifiableRootModel#addContentEntry(com.intellij.openapi.vfs.VirtualFile)
 */
public interface ContentEntry extends Synthetic {
  /**
   * Returns the root directory for the content root, if it is valid.
   *
   * @return the content root directory, or null if content entry is invalid.
   */
  @Nullable
  VirtualFile getFile();

  /**
   * Returns the URL of content root.
   * To validate returned roots, use
   * <code>{@link com.intellij.openapi.vfs.VirtualFileManager#findFileByUrl(String)}</code>
   *
   * @return URL of content root, that should never be null.
   */
  @NotNull
  String getUrl();

  /**
   * Returns the list of source roots under this content root.
   *
   * @return list of this <code>ContentEntry</code> {@link com.intellij.openapi.roots.SourceFolder}s
   */
  SourceFolder[] getSourceFolders();

  /**
   * Returns the list of directories for valid source roots under this content root.
   *
   * @return list of all valid source roots.
   */
  VirtualFile[] getSourceFolderFiles();

  /**
   * Returns the list of excluded roots under this content root.
   *
   * @return list of this <code>ContentEntry</code> {@link com.intellij.openapi.roots.ExcludeFolder}s
   */
  ExcludeFolder[] getExcludeFolders();

  /**
   * Returns the list of directories for valid excluded roots under this content root.
   *
   * @return list of all valid exclude roots.
   */
  VirtualFile[] getExcludeFolderFiles();

  /**
   * Adds a source or test source root under the content root.
   *
   * @param file         the directory to add as a source root.
   * @param isTestSource true if the directory is added as a test source root.
   * @return the object representing the added root.
   */
  SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource);

  /**
   * Adds a source or test source root with the specified package prefix under the content root.
   *
   * @param file          the directory to add as a source root.
   * @param isTestSource  true if the directory is added as a test source root.
   * @param packagePrefix the package prefix for the root to add, or an empty string if no
   *                      package prefix is required.
   * @return the object representing the added root.
   */
  SourceFolder addSourceFolder(@NotNull VirtualFile file, boolean isTestSource, @NotNull String packagePrefix);

  /**
   * Adds a source or test source root under the content root.
   *
   * @param  url the directory url to add as a source root.
   * @param isTestSource true if the directory is added as a test source root.
   * @return the object representing the added root.
   */
  SourceFolder addSourceFolder(@NotNull String url, boolean isTestSource);

  /**
   * Removes a source or test source root from this content root.
   *
   * @param sourceFolder the source root to remove (must belong to this content root).
   */
  void removeSourceFolder(@NotNull SourceFolder sourceFolder);

  /**
   * Adds an exclude root under the content root.
   *
   * @param file the directory to add as an exclude root.
   * @return the object representing the added root.
   */
  ExcludeFolder addExcludeFolder(@NotNull VirtualFile file);

  /**
   * Adds an exclude root under the content root.
   *
   * @param url the directory url to add as an exclude root.
   * @return the object representing the added root.
   */
  ExcludeFolder addExcludeFolder(@NotNull String url);

  /**
   * Removes an exclude root from this content root.
   *
   * @param excludeFolder the exclude root to remove (must belong to this content root).
   */
  void removeExcludeFolder(@NotNull ExcludeFolder excludeFolder);
}
