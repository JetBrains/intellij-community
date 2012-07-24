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
package com.intellij.psi;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

/**
 * Manages the relationship between documents and PSI trees.
 */
public abstract class PsiDocumentManager {
  public abstract boolean isCommitted(@NotNull Document document);

  /**
   * Defer action until all documents are committed
   * must be called from EDT only
   *
   * @param action to run when all documents committed
   * @return true if action was run immediately (i.e. all documents are already committed)
   */
  public abstract boolean performWhenAllCommitted(@NotNull Runnable action);

  /**
   * Returns the document manager instance for the specified project.
   *
   * @param project the project for which the document manager is requested.
   * @return the document manager instance.
   */
  public static PsiDocumentManager getInstance(@NotNull Project project) {
    return project.getComponent(PsiDocumentManager.class);
  }

  /**
   * Returns the PSI file for the specified document.
   *
   * @param document the document for which the PSI file is requested.
   * @return the PSI file instance.
   */
  @Nullable
  public abstract PsiFile getPsiFile(@NotNull Document document);

  /**
   * Returns the cached PSI file for the specified document.
   *
   * @param document the document for which the PSI file is requested.
   * @return the PSI file instance, or <code>null</code> if there is currently no cached PSI tree for the file.
   */
  @Nullable
  public abstract PsiFile getCachedPsiFile(@NotNull Document document);

  /**
   * Returns the document for the specified PSI file.
   *
   * @param file the file for which the document is requested.
   * @return the document instance, or <code>null</code> if the file is binary or has no associated document.
   */
  @Nullable
  public abstract Document getDocument(@NotNull PsiFile file);

  /**
   * Returns the cached document for the specified PSI file.
   *
   * @param file the file for which the document is requested.
   * @return the document instance, or <code>null</code> if there is currently no cached document for the file.
   */
  @Nullable
  public abstract Document getCachedDocument(@NotNull PsiFile file);

  /**
   * Commits (updates the PSI tree for) all modified but not committed documents.
   * Before a modified document is committed, accessing its PSI may return elements
   * corresponding to original (unmodified) state of the document.
   */
  public abstract void commitAllDocuments();

  /**
   * If the document is committed, runs action synchronously, otherwise schedules to execute it right after it has been committed.
   * @param document
   * @param action
   */
  public abstract void performForCommittedDocument(@NotNull Document document, @NotNull Runnable action);

  /**
   * Updates the PSI tree for the specified document.
   * Before a modified document is committed, accessing its PSI may return elements
   * corresponding to original (unmodified) state of the document.
   *
   * @param document the document to commit.
   */
  public abstract void commitDocument(@NotNull Document document);

  /**
   * Returns the list of documents which have been modified but not committed.
   *
   * @return the list of uncommitted documents.
   * @see #commitDocument(com.intellij.openapi.editor.Document)
   */
  @NotNull
  public abstract Document[] getUncommittedDocuments();

  /**
   * Checks if the specified document has been committed.
   *
   * @param document the document to check.
   * @return true if the document was modified but not committed, false otherwise
   * @see #commitDocument(com.intellij.openapi.editor.Document)
   */
  public abstract boolean isUncommited(@NotNull Document document);

  /**
   * Checks if any modified documents have not been committed.
   *
   * @return true if there are uncommitted documents, false otherwise
   */
  public abstract boolean hasUncommitedDocuments();

  /**
   * Commits the documents and runs the specified operation, which does not return a value, in a read action.
   * Can be called from a thread other than the Swing dispatch thread.
   *
   * @param runnable the operation to execute.
   */
  public abstract void commitAndRunReadAction(@NotNull Runnable runnable);

  /**
   * Commits the documents and runs the specified operation, which returns a value, in a read action.
   * Can be called from a thread other than the Swing dispatch thread.
   *
   * @param computation the operation to execute.
   * @return the value returned by the operation.
   */
  public abstract <T> T commitAndRunReadAction(@NotNull Computable<T> computation);

  /**
   * Listener for receiving notifications about creation of {@link Document} and {@link PsiFile} instances.
   */
  public interface Listener extends EventListener {
    /**
     * Called when a document instance is created for a file.
     *
     * @param document the created document instance.
     * @param psiFile the file for which the document was created.
     * @see PsiDocumentManager#getDocument(PsiFile)
     */
    void documentCreated(@NotNull Document document, PsiFile psiFile);

    /**
     * Called when a file instance is created for a document.
     *
     * @param file the created file instance.
     * @param document the document for which the file was created.
     * @see PsiDocumentManager#getDocument(PsiFile)
     */
    void fileCreated(@NotNull PsiFile file, @NotNull Document document);
  }

  /**
   * Adds a listener for receiving notifications about creation of {@link Document} and {@link PsiFile} instances.
   *
   * @param listener the listener to add.
   */
  public abstract void addListener(@NotNull Listener listener);

  /**
   * Removes a listener for receiving notifications about creation of {@link Document} and {@link PsiFile} instances.
   *
   * @param listener the listener to add.
   */
  public abstract void removeListener(@NotNull Listener listener);

  public abstract boolean isDocumentBlockedByPsi(@NotNull Document doc);

  public abstract void doPostponedOperationsAndUnblockDocument(@NotNull Document doc);
}
