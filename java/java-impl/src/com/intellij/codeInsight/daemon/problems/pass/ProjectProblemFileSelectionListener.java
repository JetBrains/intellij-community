// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.problems.SnapshotUpdater;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.BulkAwareDocumentListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.util.ObjectUtils.tryCast;

class ProjectProblemFileSelectionListener implements FileEditorManagerListener, BulkAwareDocumentListener, ProjectManagerListener {

  private static final Key<Boolean> PROJECT_PROBLEM_FILE_CHANGE_LISTENER_KEY = Key.create("ProjectProblemFileChangeListenerKey");

  private final Project myProject;

  private ProjectProblemFileSelectionListener(Project project) {
    myProject = project;
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    VirtualFile oldFile = event.getOldFile();
    if (oldFile != null) removeListener(oldFile);
    VirtualFile newFile = event.getNewFile();
    if (newFile == null) return;
    PsiJavaFile psiFile = tryCast(PsiManager.getInstance(myProject).findFile(newFile), PsiJavaFile.class);
    if (psiFile == null) return;
    ProjectProblemPassUtils.removeOldInlays(psiFile);
    addListener(newFile);
  }

  @Override
  public void projectClosingBeforeSave(@NotNull Project project) {
    Arrays.stream(FileEditorManager.getInstance(project).getSelectedFiles()).forEach(vf -> removeListener(vf));
  }

  private void addListener(VirtualFile virtualFile) {
    if (virtualFile instanceof VirtualFileWindow || !virtualFile.isValid()) return;
    PsiJavaFile psiFile = tryCast(PsiManager.getInstance(myProject).findFile(virtualFile), PsiJavaFile.class);
    if (psiFile == null) return;
    Document document = getDocument(virtualFile);
    if (document == null) return;
    if (Boolean.TRUE.equals(document.getUserData(PROJECT_PROBLEM_FILE_CHANGE_LISTENER_KEY))) return;
    document.addDocumentListener(this);
    document.putUserData(PROJECT_PROBLEM_FILE_CHANGE_LISTENER_KEY, true);
  }

  private void removeListener(VirtualFile virtualFile) {
    if (virtualFile == null || virtualFile instanceof VirtualFileWindow || !virtualFile.isValid()) return;
    if (!(PsiManager.getInstance(myProject).findFile(virtualFile) instanceof PsiJavaFile)) return;
    Document document = getDocument(virtualFile);
    if (document == null) return;
    if (!Boolean.TRUE.equals(document.getUserData(PROJECT_PROBLEM_FILE_CHANGE_LISTENER_KEY))) return;
    document.removeDocumentListener(this);
    document.putUserData(PROJECT_PROBLEM_FILE_CHANGE_LISTENER_KEY, null);
  }

  @Nullable
  private static Document getDocument(@NotNull VirtualFile virtualFile) {
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document document = documentManager.getDocument(virtualFile);
    if (document == null) return null;
    return document;
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    Document document = event.getDocument();
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    VirtualFile virtualFile = documentManager.getFile(document);
    if (virtualFile == null) return;
    CharSequence content = document.getImmutableCharSequence();
    PsiJavaFile psiFile = tryCast(PsiManager.getInstance(myProject).findFile(virtualFile), PsiJavaFile.class);
    if (psiFile == null) return;
    SnapshotUpdater.storeContent(psiFile, content);
    removeListener(virtualFile);
  }

  public static class MyStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (!Registry.is("project.problems.view") && !ApplicationManager.getApplication().isUnitTestMode()) return;
      ProjectProblemFileSelectionListener listener = new ProjectProblemFileSelectionListener(project);
      Arrays.stream(FileEditorManager.getInstance(project).getSelectedFiles()).forEach(vf -> listener.addListener(vf));
      project.getMessageBus().connect().subscribe(FILE_EDITOR_MANAGER, listener);
      ProjectManager.getInstance().addProjectManagerListener(project, listener);
    }
  }
}
