// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.problems.SnapshotUpdater;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.BulkAwareDocumentListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

class ProjectProblemFileSelectionListener implements FileEditorManagerListener, BulkAwareDocumentListener {

  private static final Key<Boolean> PROJECT_PROBLEM_FILE_CHANGE_LISTENER_KEY = Key.create("ProjectProblemFileChangeListenerKey");

  private final Project myProject;

  private ProjectProblemFileSelectionListener(Project project) {
    myProject = project;
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    VirtualFile virtualFile = event.getNewFile();
    if (virtualFile == null) return;
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    if (psiFile == null) return;
    registerListener(virtualFile);
    ProjectProblemPassUtils.removeOldInlays(psiFile);
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    Document document = event.getDocument();
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    VirtualFile virtualFile = documentManager.getFile(document);
    if (virtualFile == null) return;
    CharSequence content = document.getImmutableCharSequence();
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(virtualFile);
    if (psiFile == null) return;
    SnapshotUpdater.storeContent(psiFile, content);
  }

  private void registerListener(@NotNull VirtualFile virtualFile) {
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document document = documentManager.getDocument(virtualFile);
    if (document == null) return;
    if (Boolean.TRUE.equals(document.getUserData(PROJECT_PROBLEM_FILE_CHANGE_LISTENER_KEY))) return;
    document.addDocumentListener(this);
    document.putUserData(PROJECT_PROBLEM_FILE_CHANGE_LISTENER_KEY, true);
  }

  public static class MyStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (!Registry.is("project.problems.view") && !ApplicationManager.getApplication().isUnitTestMode()) return;
      ProjectProblemFileSelectionListener listener = new ProjectProblemFileSelectionListener(project);
      project.getMessageBus().connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
      Arrays.stream(FileEditorManager.getInstance(project).getSelectedFiles()).forEach(vf -> listener.registerListener(vf));
    }
  }
}
