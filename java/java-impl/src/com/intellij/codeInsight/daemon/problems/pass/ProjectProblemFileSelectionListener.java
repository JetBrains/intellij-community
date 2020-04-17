// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.problems.FileStateUpdater;
import com.intellij.codeInsight.hints.InlayHintsSettings;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInsight.daemon.problems.pass.ProjectProblemInlaySettingsProvider.hintsEnabled;
import static com.intellij.util.ObjectUtils.tryCast;

class ProjectProblemFileSelectionListener implements FileEditorManagerListener, InlayHintsSettings.SettingsListener, BulkFileListener {

  private final Project myProject;

  private ProjectProblemFileSelectionListener(Project project) {
    myProject = project;
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    if (!hintsEnabled()) return;
    VirtualFile oldFile = event.getOldFile();
    VirtualFile newFile = event.getNewFile();
    TextEditor oldEditor = tryCast(event.getOldEditor(), TextEditor.class);
    if (oldFile == null || oldEditor == null || oldFile instanceof VirtualFileWindow || !oldFile.isValid() || oldFile.equals(newFile)) return;
    PsiJavaFile psiJavaFile = tryCast(PsiManager.getInstance(myProject).findFile(oldFile), PsiJavaFile.class);
    if (psiJavaFile == null) return;
    ProjectProblemPassUtils.removeInlays(oldEditor.getEditor());
    FileStateUpdater.setPreviousState(psiJavaFile);
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    PsiManager psiManager = PsiManager.getInstance(myProject);
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (VFileEvent e : events) {
      if (!(e instanceof VFileDeleteEvent)) continue;
      VirtualFile virtualFile = ((VFileDeleteEvent)e).getFile();
      if (!fileIndex.isInContent(virtualFile)) continue;
      PsiFile psiFile = psiManager.findFile(virtualFile);
      if (psiFile == null) continue;
      FileStateUpdater.removeState(psiFile);
    }
  }

  @Override
  public void settingsChanged() {
    if (!hintsEnabled()) onHintsDisabled();
  }

  @Override
  public void languageStatusChanged() {
    if (!hintsEnabled()) onHintsDisabled();
  }

  @Override
  public void globalEnabledStatusChanged(boolean newEnabled) {
    if (!hintsEnabled()) onHintsDisabled();
  }

  private void onHintsDisabled() {
    FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
    PsiManager psiManager = PsiManager.getInstance(myProject);
    for (FileEditor selectedEditor : editorManager.getSelectedEditors()) {
      TextEditor textEditor = tryCast(selectedEditor, TextEditor.class);
      if (textEditor == null) continue;
      VirtualFile virtualFile = selectedEditor.getFile();
      if (virtualFile == null) continue;
      PsiJavaFile psiJavaFile = tryCast(psiManager.findFile(virtualFile), PsiJavaFile.class);
      if (psiJavaFile == null) continue;
      ProjectProblemPassUtils.removeInlays(textEditor.getEditor());
      FileStateUpdater.setPreviousState(psiJavaFile);
    }
  }

  public static class MyStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (!Registry.is("project.problems.view") && !ApplicationManager.getApplication().isUnitTestMode()) return;
      ProjectProblemFileSelectionListener listener = new ProjectProblemFileSelectionListener(project);
      MessageBusConnection connection = project.getMessageBus().connect();
      connection.subscribe(FILE_EDITOR_MANAGER, listener);
      connection.subscribe(InlayHintsSettings.getINLAY_SETTINGS_CHANGED(), listener);
      connection.subscribe(VirtualFileManager.VFS_CHANGES, listener);
    }
  }
}
