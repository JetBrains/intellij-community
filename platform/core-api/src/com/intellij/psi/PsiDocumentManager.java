// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EventListener;

/**
 * Manages the relationship between documents and PSI trees.
 */
public abstract class PsiDocumentManager {
  /**
   * Checks if the PSI tree for the specified document is up to date (its state reflects the latest changes made
   * to the document content).
   *
   * @param document the document to check.
   * @return true if the PSI tree for the document is up to date, false otherwise.
   */
  public abstract boolean isCommitted(@NotNull Document document);

  /**
   * Returns the document manager instance for the specified project.
   *
   * @param project the project for which the document manager is requested.
   * @return the document manager instance.
   */
  public static PsiDocumentManager getInstance(@NotNull Project project) {
    return project.getService(PsiDocumentManager.class);
  }

  /**
   * Returns the PSI file for the specified document.
   *
   * @param document the document for which the PSI file is requested.
   * @return the PSI file instance.
   */
  @RequiresBackgroundThread(generateAssertion = false)
  public abstract @Nullable PsiFile getPsiFile(@NotNull Document document);

  /**
   * Returns the cached PSI file for the specified document.
   *
   * @param document the document for which the PSI file is requested.
   * @return the PSI file instance, or {@code null} if there is currently no cached PSI tree for the file.
   */
  public abstract @Nullable PsiFile getCachedPsiFile(@NotNull Document document);

  /**
   * Returns the document for the specified PSI file.
   *
   * @param file the file for which the document is requested.
   * @return the document instance, or {@code null} if the file is binary or has no associated document.
   */
  public abstract @Nullable Document getDocument(@NotNull PsiFile file);

  /**
   * Returns the cached document for the specified PSI file.
   *
   * @param file the file for which the document is requested.
   * @return the document instance, or {@code null} if there is currently no cached document for the file.
   */
  public abstract @Nullable Document getCachedDocument(@NotNull PsiFile file);

  /**
   * Commits (updates the PSI tree for) all modified but not committed documents.
   * Before a modified document is committed, accessing its PSI may return elements
   * corresponding to original (unmodified) state of the document.<p/>
   * <p>
   * Should be called in UI thread in a write-safe context (see {@link com.intellij.openapi.application.TransactionGuard})
   */
  public abstract void commitAllDocuments();

  /**
   * Commits all modified but not committed documents under modal dialog (see {@link PsiDocumentManager#commitAllDocuments()}
   * Should be called in UI thread and outside write-action
   *
   * @return true if the operation completed successfully, false if it was canceled.
   */
  public abstract boolean commitAllDocumentsUnderProgress();

  /**
   * If the {@code document} is committed, run {@code action} immediately.
   * Otherwise, schedule the execution of the {@code action} sometime in the future right after the {@code document} is committed.
   */
  public abstract void performForCommittedDocument(@NotNull Document document, @NotNull Runnable action);

  /**
   * Updates the PSI tree for the specified document.
   * Before a modified document is committed, accessing its PSI may return elements
   * corresponding to original (unmodified) state of the document.<p/>
   * <p>
   * For documents with event-system-enabled PSI ({@link FileViewProvider#isEventSystemEnabled()}), should be called in UI thread in a write-safe context (see {@link com.intellij.openapi.application.TransactionGuard}).
   * For other documents, it can be called in background thread with read access. It's the responsibility of the caller to properly synchronize
   * that PSI and ensure no other threads are reading or modifying it concurrently.
   *
   * @param document the document to commit.
   */
  public abstract void commitDocument(@NotNull Document document);

  /**
   * @return the document text that PSI should be based upon. For changed documents, it's their old text until the document is committed.
   * This sequence is immutable.
   * @see com.intellij.util.text.ImmutableCharSequence
   */
  public abstract @NotNull CharSequence getLastCommittedText(@NotNull Document document);

  /**
   * @return for uncommitted documents, the last stamp before the document change: the same stamp that the current PSI should have.
   * For committed documents, just their stamp.
   * @see Document#getModificationStamp()
   * @see FileViewProvider#getModificationStamp()
   */
  public abstract long getLastCommittedStamp(@NotNull Document document);

  /**
   * Returns the document for specified PsiFile intended to be used when working with committed PSI, e.g., outside dispatch thread.
   *
   * @param file the file for which the document is requested.
   * @return an immutable document corresponding to the current PSI state. For committed documents, the contents and timestamp are equal to
   * the ones of {@link #getDocument(PsiFile)}. For uncommitted documents, the text is {@link #getLastCommittedText(Document)} and
   * the modification stamp is {@link #getLastCommittedStamp(Document)}.
   */
  public abstract @Nullable Document getLastCommittedDocument(@NotNull PsiFile file);

  /**
   * Returns the array of documents which have been modified but not committed.
   *
   * @return the array of uncommitted documents.
   * @see #commitDocument(Document)
   */
  public abstract @NotNull Document @NotNull [] getUncommittedDocuments();

  /**
   * Checks if the specified document has been committed.
   *
   * @param document the document to check.
   * @return true if the document was modified but not committed, false otherwise
   * @see #commitDocument(Document)
   */
  public abstract boolean isUncommited(@NotNull Document document);

  /**
   * Checks if any modified documents have not been committed.
   *
   * @return true if there are uncommitted documents, false otherwise
   */
  public abstract boolean hasUncommitedDocuments();

  /**
   * @return if any modified documents with event-system-enabled PSI have not been committed.
   * @see FileViewProvider#isEventSystemEnabled()
   */
  @ApiStatus.Experimental
  public boolean hasEventSystemEnabledUncommittedDocuments() {
    return hasUncommitedDocuments();
  }

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
   * Reparses the specified set of files after an external configuration change that would cause them to be parsed differently
   * (for example, a language level change in the settings).
   *
   * @param files            the files to reparse.
   * @param includeOpenFiles if true, the files opened in editor tabs will also be reparsed.
   */
  public abstract void reparseFiles(final @NotNull Collection<? extends VirtualFile> files, final boolean includeOpenFiles);

  /**
   * Listener for receiving notifications about creation of {@link Document} and {@link PsiFile} instances.
   */
  public interface Listener extends EventListener {
    /**
     * Called when a document instance is created for a file.
     *
     * @param document the created document instance.
     * @param psiFile  the file for which the document was created.
     * @see PsiDocumentManager#getDocument(PsiFile)
     */
    void documentCreated(@NotNull Document document, @Nullable PsiFile psiFile);

    /**
     * Called when a file instance is created for a document.
     *
     * @param file     the created file instance.
     * @param document the document for which the file was created.
     * @see PsiDocumentManager#getDocument(PsiFile)
     */
    default void fileCreated(@NotNull PsiFile file, @NotNull Document document) {
    }
  }

  /**
   * Checks if the PSI tree corresponding to the specified document has been modified and the changes have not
   * yet been applied to the document. Documents in that state cannot be modified directly, because such changes
   * would conflict with the pending PSI changes. Changes made through PSI are always applied at the end of a write action,
   * and can be applied in the middle of a write action by calling {@link #doPostponedOperationsAndUnblockDocument}.
   *
   * @param doc the document to check.
   * @return true if the corresponding PSI has changes that haven't been applied to the document.
   */
  public abstract boolean isDocumentBlockedByPsi(@NotNull Document doc);

  /**
   * Applies pending changes made through the PSI to the specified document.
   *
   * @param doc the document to apply the changes to.
   */
  public abstract void doPostponedOperationsAndUnblockDocument(@NotNull Document doc);

  /**
   * Defer action until all documents with event-system-enabled PSI are committed.
   * Must be called from the EDT only.
   *
   * @param action to run when all documents are committed
   * @return true if action was run immediately (i.e., all documents are already committed)
   */
  public abstract boolean performWhenAllCommitted(@NotNull Runnable action);

  /**
   * Same as {@link #performLaterWhenAllCommitted(ModalityState, Runnable)} using {@link ModalityState#defaultModalityState()}
   */
  public abstract void performLaterWhenAllCommitted(@NotNull Runnable runnable);

  /**
   * Schedule the {@code runnable} to be executed on Swing thread when all documents with event-system-enabled PSI
   * are committed at some later moment in a given modality state.
   * The {@code runnable} is guaranteed to be invoked when no write action is running, and not immediately.
   * If the project is disposed before this moment, the {@code runnable} is not executed.
   */
  public abstract void performLaterWhenAllCommitted(@NotNull ModalityState modalityState, @NotNull Runnable runnable);
}
