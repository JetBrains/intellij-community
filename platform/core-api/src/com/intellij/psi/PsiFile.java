/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.lang.FileASTNode;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * A PSI element representing a file.
 * <p/>
 * Please see <a href="http://confluence.jetbrains.net/display/IDEADEV/IntelliJ+IDEA+Architectural+Overview">IntelliJ IDEA Architectural Overview </a>
 * for high-level overview.
 *
 * @see com.intellij.openapi.actionSystem.LangDataKeys#PSI_FILE
 * @see PsiElement#getContainingFile()
 * @see PsiManager#findFile(VirtualFile)
 * @see PsiDocumentManager#getPsiFile(com.intellij.openapi.editor.Document)
 */
public interface PsiFile extends PsiFileSystemItem {
  /**
   * The empty array of PSI files which can be reused to avoid unnecessary allocations.
   */
  PsiFile[] EMPTY_ARRAY = new PsiFile[0];

  /**
   * Returns the virtual file corresponding to the PSI file.
   *
   * @return the virtual file, or null if the file exists only in memory.
   */
  @Override
  VirtualFile getVirtualFile();

  /**
   * Returns the directory containing the file.
   *
   * @return the containing directory, or null if the file exists only in memory.
   */
  PsiDirectory getContainingDirectory();

  @Override
  PsiDirectory getParent();

  /**
   * Gets the modification stamp value. Modification stamp is a value changed by any modification
   * of the content of the file. Note that it is not related to the file modification time.
   *
   * @return the modification stamp value
   * @see VirtualFile#getModificationStamp()
   */
  long getModificationStamp();

  /**
   * If the file is a non-physical copy of a file, returns the original file which had
   * been copied. Otherwise, returns the same file.
   *
   * @return the original file of a copy, or the same file if the file is not a copy.
   */
  @NotNull
  PsiFile getOriginalFile();

  /**
   * Returns the file type for the file.
   *
   * @return the file type instance.
   */
  @NotNull
  FileType getFileType();

  /**
   * If the file contains multiple interspersed languages, returns the roots for
   * PSI trees for each of these languages. (For example, a JSPX file contains JSP,
   * XML and Java trees.)
   *
   * @return the array of PSI roots, or a single-element array containing {@code this}
   * if the file has only a single language.
   * @deprecated Use {@link FileViewProvider#getAllFiles()} instead.
   */
  @Deprecated
  @NotNull
  PsiFile[] getPsiRoots();

  @NotNull
  FileViewProvider getViewProvider();

  @Override
  FileASTNode getNode();

  /**
   * Called by the PSI framework when the contents of the file changes.
   * If you override this method, you <b>must</b> call the base class implementation.
   * While this method can be used to invalidate file-level caches, it is more much safe to invalidate them in  {@link #clearCaches()}
   * since file contents can be reloaded completely (without any specific subtree change) without this method being called.
   */
  void subtreeChanged();

  /**
   * Invalidate any file-specific cache in this method. It is called on file file content change.
   * If you override this method, you <b>must</b> call the base class implementation.
   */
  default void clearCaches() {

  }
}
