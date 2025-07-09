// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ProjectDisposeAwareDocumentListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.impl.PsiDocumentTransactionListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

@ApiStatus.Internal
public final class DocumentAfterCommitListener {
  /**
   * Allows to listen for {@link Document} commit events and fire {@code documentCommittedListener} after the PSI commit finished
   */
  public static void listen(@NotNull Project project,
                            @NotNull Disposable parentDisposable,
                            @NotNull Consumer<? super Document> documentCommittedListener) {
    /*NOT STATIC!!!*/ final Key<Boolean> UPDATE_ON_COMMIT_ENGAGED = Key.create("UPDATE_ON_COMMIT_ENGAGED"); // not static because we could have several DocumentAfterCommitListener instances with different listeners
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(
      ProjectDisposeAwareDocumentListener.create(project, new DocumentListener() {
      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent event) {
        if (project.isDisposed()) return;
        Document document = event.getDocument();
        PsiDocumentManagerImpl documentManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(project);
        if (documentManager.getSynchronizer().isInSynchronization(document)) {
          return;
        }

        PsiFile psi = documentManager.getCachedPsiFile(document);
        if (psi == null || !psi.getViewProvider().isEventSystemEnabled()) {
          return;
        }

        if (document.getUserData(UPDATE_ON_COMMIT_ENGAGED) == null) {
          document.putUserData(UPDATE_ON_COMMIT_ENGAGED, Boolean.TRUE);
          documentManager.addRunOnCommit(document, d -> {
            if (d.getUserData(UPDATE_ON_COMMIT_ENGAGED) != null) {
              updateChangesForDocument(d, documentCommittedListener);
              d.putUserData(UPDATE_ON_COMMIT_ENGAGED, null);
            }
          });
        }
      }
    }), parentDisposable);

    project.getMessageBus().connect().subscribe(PsiDocumentTransactionListener.TOPIC, new PsiDocumentTransactionListener() {
      @Override
      public void transactionStarted(@NotNull Document doc, @NotNull PsiFile psiFile) {
      }

      @Override
      public void transactionCompleted(@NotNull Document document, @NotNull PsiFile psiFile) {
        updateChangesForDocument(document, documentCommittedListener);
        document.putUserData(UPDATE_ON_COMMIT_ENGAGED, null); // ensure we don't call updateChangesForDocument() twice which can lead to the whole file re-highlight
      }
    });
  }

  private static void updateChangesForDocument(@NotNull Document document, @NotNull Consumer<? super Document> documentCommittedListener) {
    documentCommittedListener.accept(document);
  }
}
