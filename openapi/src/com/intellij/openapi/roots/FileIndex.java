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
