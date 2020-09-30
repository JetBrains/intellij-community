// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.problems.FileStateUpdater;
import com.intellij.codeInsight.hints.InlayHintsSettings;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInsight.daemon.problems.pass.ProjectProblemHintProvider.hintsEnabled;
import static com.intellij.util.ObjectUtils.tryCast;

final class ProjectProblemFileSelectionListener implements FileEditorManagerListener,
                                                           InlayHintsSettings.SettingsListener,
                                                           BulkFileListener {
  private final Project myProject;

  private ProjectProblemFileSelectionListener(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    if (!hintsEnabled()) return;

    PsiJavaFile oldJavaFile = getJavaFile(myProject, event.getOldFile());
    if (oldJavaFile != null) {
      Editor oldEditor = getEditor(event.getOldEditor());
      if (oldEditor != null) ProjectProblemUtils.updateTimestamp(oldJavaFile, oldEditor);
    }

    PsiJavaFile newJavaFile = getJavaFile(myProject, event.getNewFile());
    if (newJavaFile == null || oldJavaFile == newJavaFile) return;
    Editor newEditor = getEditor(event.getNewEditor());
    if (newEditor == null || !ProjectProblemUtils.isProjectUpdated(newJavaFile, newEditor)) return;
    FileStateUpdater.setPreviousState(newJavaFile);
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (VFileEvent e : events) {
      VirtualFile changedFile = e.getFile();
      if (changedFile == null) continue;
      if (e instanceof VFileDeleteEvent) {
        if (!fileIndex.isInContent(changedFile)) continue;
        PsiJavaFile removedJavaFile = getJavaFile(myProject, changedFile);
        if (removedJavaFile == null) continue;
        FileStateUpdater.removeState(removedJavaFile);
      }
      if (e instanceof VFileContentChangeEvent || e instanceof VFileDeleteEvent) {
        TextEditor editor = tryCast(FileEditorManager.getInstance(myProject).getSelectedEditor(), TextEditor.class);
        if (editor == null) continue;
        VirtualFile selectedFile = editor.getFile();
        if (selectedFile == null || changedFile.equals(selectedFile)) continue;
        PsiJavaFile selectedJavaFile = getJavaFile(myProject, selectedFile);
        if (selectedJavaFile == null) continue;
        editor.getEditor();
        FileStateUpdater.setPreviousState(selectedJavaFile);
      }
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
    for (FileEditor selectedEditor : editorManager.getSelectedEditors()) {
      VirtualFile virtualFile = selectedEditor.getFile();
      if (virtualFile == null) continue;
      PsiJavaFile psiJavaFile = getJavaFile(myProject, virtualFile);
      if (psiJavaFile == null) continue;
      FileStateUpdater.setPreviousState(psiJavaFile);
    }
  }

  private static @Nullable PsiJavaFile getJavaFile(@NotNull Project project, @Nullable VirtualFile file) {
    if (file == null || file instanceof VirtualFileWindow || !file.isValid()) return null;
    return tryCast(PsiManager.getInstance(project).findFile(file), PsiJavaFile.class);
  }

  private static @Nullable Editor getEditor(@Nullable FileEditor fileEditor) {
    TextEditor textEditor = tryCast(fileEditor, TextEditor.class);
    return textEditor == null ? null : textEditor.getEditor();
  }

  public static class MyStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      ProjectProblemFileSelectionListener listener = new ProjectProblemFileSelectionListener(project);
      MessageBusConnection connection = project.getMessageBus().connect();
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
      connection.subscribe(InlayHintsSettings.getINLAY_SETTINGS_CHANGED(), listener);
      connection.subscribe(VirtualFileManager.VFS_CHANGES, listener);
    }
  }
}
