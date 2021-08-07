// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actionsOnSave.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ActionsOnSaveFileDocumentManagerListener implements FileDocumentManagerListener {
  private static final ExtensionPointName<ActionOnSave> EP_NAME = new ExtensionPointName<>("com.intellij.actionOnSave");

  public abstract static class ActionOnSave {
    /**
     * Invoked in EDT, maybe inside write action. Should be fast. It's ok to return <code>true</code> and then do nothing in {@link #processDocuments}
     *
     * @param project it's initialized, open, not disposed, and not the default one; no need to double-check in implementations
     */
    public boolean isEnabledForProject(@NotNull Project project) { return false; }

    /**
     * Invoked in EDT, not inside write action. Potentially long implementations should run with modal progress synchronously.
     * Implementations don't need to save modified documents. Note that the passed documents may be unsaved if already modified by some other save action.
     */
    public void processDocuments(@NotNull Project project, @NotNull Document @NotNull [] documents) { }
  }

  /**
   * Not empty state of this set means that processing has been scheduled (invokeLater(...)) but bot yet performed.
   */
  private final Set<Document> myDocumentsToProcess = new HashSet<>();

  @Override
  public void beforeAllDocumentsSaving() {
    Document[] documents = FileDocumentManager.getInstance().getUnsavedDocuments();
    if (documents.length == 0) {
      return;
    }

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      for (ActionOnSave saveAction : EP_NAME.getExtensionList()) {
        if (saveAction.isEnabledForProject(project)) {
          scheduleDocumentsProcessing(documents);
          return;
        }
      }
    }
  }

  private void scheduleDocumentsProcessing(Document[] documents) {
    boolean processingAlreadyScheduled = !myDocumentsToProcess.isEmpty();

    myDocumentsToProcess.addAll(Arrays.asList(documents));

    if (!processingAlreadyScheduled) {
      ApplicationManager.getApplication().invokeLater(() -> processSavedDocuments(), ModalityState.NON_MODAL);
    }
  }

  private void processSavedDocuments() {
    Document[] documents = myDocumentsToProcess.toArray(Document.EMPTY_ARRAY);
    myDocumentsToProcess.clear();

    // Although invokeLater() is called with ModalityState.NON_MODAL argument, somehow this might be called in modal context (for example on Commit File action)
    // It's quite weird if save action progress appears or documents get changed in modal context, let's ignore the request.
    if (ModalityState.current() != ModalityState.NON_MODAL) return;

    FileDocumentManager manager = FileDocumentManager.getInstance();

    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      ProjectFileIndex index = ProjectFileIndex.getInstance(project);
      List<Document> projectDocuments = ContainerUtil.filter(documents, document -> {
        VirtualFile file = manager.getFile(document);
        return file != null && index.isInContent(file);
      });

      if (projectDocuments.isEmpty()) {
        continue;
      }

      for (ActionOnSave saveAction : EP_NAME.getExtensionList()) {
        if (saveAction.isEnabledForProject(project)) {
          saveAction.processDocuments(project, projectDocuments.toArray(Document.EMPTY_ARRAY));
        }
      }
    }

    for (Document document : documents) {
      manager.saveDocument(document);
    }
  }
}
