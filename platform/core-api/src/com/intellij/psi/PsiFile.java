// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.FileASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A PSI element representing a file.
 * <p/>
 * Please see <a href="https://plugins.jetbrains.com/docs/intellij/psi-files.html">PSI Files</a>
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
   * <p>
   * If you want to get a non-null virtual file consider using {@link FileViewProvider#getVirtualFile()}<br>
   * ({@code psiFile.getViewProvider().getVirtualFile()})
   *
   * @return the virtual file, or {@code null} if the file exists only in memory
   * @see FileViewProvider#getVirtualFile()
   */
  @Override
  VirtualFile getVirtualFile();

  /**
   * Returns the directory containing the file.
   *
   * @return the containing directory, or {@code null} if the file exists only in memory.
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
  PsiFile @NotNull [] getPsiRoots();

  @NotNull
  FileViewProvider getViewProvider();

  /**
   * Returns the document corresponding to this file. Note that the document content could be out of sync
   * with the {@link PsiFile}. It's a caller responsibility to synchronize the document and the {@code PsiFile}
   * using {@link PsiDocumentManager#commitDocument(Document)} and 
   * {@link PsiDocumentManager#doPostponedOperationsAndUnblockDocument(Document)}.
   * 
   * @return the document corresponding to this file
   * @throws UnsupportedOperationException if the document is not associated with this type of file
   * (for example, if the file is binary).
   */
  @ApiStatus.NonExtendable
  default @NotNull Document getFileDocument() {
    Document document = getViewProvider().getDocument();
    if (document == null) {
      throw new UnsupportedOperationException(
        "No document is available for file " + getClass() + "; virtualFile: " + PsiUtilCore.getVirtualFile(this));
    }
    return document;
  }

  @Override
  FileASTNode getNode();

  /**
   * Called by the PSI framework when the contents of the file change.
   * If you override this method, you <b>must</b> call the base class implementation.
   * While this method can be used to invalidate file-level caches, it is more much safe to invalidate them in {@link #clearCaches()}
   * since file contents can be reloaded completely (without any specific subtree change) without this method being called.
   */
  void subtreeChanged();

  /**
   * Invalidate any file-specific cache in this method. It is called on file content change.
   * If you override this method, you <b>must</b> call the base class implementation.
   */
  default void clearCaches() {}

  /**
   * @return the element type of the file node, but possibly in an efficient node that doesn't instantiate the node.
   */
  default @Nullable IFileElementType getFileElementType() {
    return ObjectUtils.tryCast(PsiUtilCore.getElementType(getNode()), IFileElementType.class);
  }
}
