// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.codeInsight.daemon.problems.FileStateCache;
import com.intellij.codeInsight.daemon.problems.FileStateUpdater;
import com.intellij.codeInsight.hints.InlayHintsPassFactory;
import com.intellij.codeInsight.hints.InlayHintsSettings;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.impl.EditorImpl;
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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInsight.daemon.problems.pass.ProjectProblemCodeVisionProvider.hintsEnabled;
import static com.intellij.util.ObjectUtils.tryCast;

/**
 * Listener that reacts to user initiated changes and updates current problems state.
 * <p>
 * Events that are handled by this listener:<br>
 * 1. selection change (user opened different file) -> store current timestamp for closed file and restores state for new file<br>
 * 2. VFS changes -> remove states for deleted files, remove problems for updated files (in order to recalculate them later)<br>
 * 3. hints settings change -> rollback file state, so that there are no reported problems yet (but they can be found using a rolled-back state)<br>
 * 4. refactoring done for member -> rollback member file state<br>
 * 5. PSI tree changed -> rollback file state for all the editors with this file<br>
 */
final class ProjectProblemFileSelectionListener extends PsiTreeChangeAdapter implements FileEditorManagerListener,
                                                                                        InlayHintsSettings.SettingsListener,
                                                                                        BulkFileListener,
                                                                                        RefactoringEventListener {
  private final Project myProject;

  private ProjectProblemFileSelectionListener(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    if (!hintsEnabled(myProject)) return;

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
    boolean isInSplitEditorMode = FileEditorManager.getInstance(myProject).getSelectedEditors().length > 1;
    if (isInSplitEditorMode) {
      InlayHintsPassFactory.Companion.forceHintsUpdateOnNextPass();
    }
  }

  @Override
  public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (VFileEvent e : events) {
      if (e instanceof VFileDeleteEvent) {
        VirtualFile changedFile = e.getFile();
        if (!fileIndex.isInContent(changedFile)) continue;
        PsiJavaFile removedJavaFile = getJavaFile(myProject, changedFile);
        if (removedJavaFile == null) continue;
        FileStateUpdater.removeState(removedJavaFile);
      }
      if (e instanceof VFileContentChangeEvent || e instanceof VFileDeleteEvent) {
        VirtualFile selectedFile = getSelectedFile();
        if (selectedFile == null || e.getFile().equals(selectedFile)) continue;
        PsiJavaFile selectedJavaFile = getJavaFile(myProject, selectedFile);
        if (selectedJavaFile == null) continue;
        FileStateUpdater.setPreviousState(selectedJavaFile);
      }
    }
  }

  @Nullable
  private VirtualFile getSelectedFile() {
    TextEditor editor = tryCast(FileEditorManager.getInstance(myProject).getSelectedEditor(), TextEditor.class);
    return editor == null ? null : editor.getFile();
  }

  @Override
  public void settingsChanged() {
    if (!hintsEnabled(myProject)) onHintsDisabled();
  }

  @Override
  public void languageStatusChanged() {
    if (!hintsEnabled(myProject)) onHintsDisabled();
  }

  @Override
  public void globalEnabledStatusChanged(boolean newEnabled) {
    if (!hintsEnabled(myProject)) onHintsDisabled();
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

  @Override
  public void refactoringStarted(@NotNull String refactoringId, @Nullable RefactoringEventData beforeData) {
  }

  @Override
  public void refactoringDone(@NotNull String refactoringId, @Nullable RefactoringEventData afterData) {
    if (afterData == null) return;
    PsiMember member = tryCast(afterData.getUserData(RefactoringEventData.PSI_ELEMENT_KEY), PsiMember.class);
    if (member == null) return;
    PsiJavaFile psiJavaFile = tryCast(member.getContainingFile(), PsiJavaFile.class);
    if (psiJavaFile == null) return;
    FileStateUpdater.setPreviousState(psiJavaFile);
  }

  @Override
  public void conflictsDetected(@NotNull String refactoringId, @NotNull RefactoringEventData conflictsData) {
  }

  @Override
  public void undoRefactoring(@NotNull String refactoringId) {
    VirtualFile selectedFile = getSelectedFile();
    if (selectedFile == null) return;
    PsiJavaFile psiJavaFile = getJavaFile(myProject, selectedFile);
    if (psiJavaFile == null) return;
    FileStateUpdater.setPreviousState(psiJavaFile);
  }

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
    updateFileState(event.getFile());
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
    updateFileState(event.getFile());
  }

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
    updateFileState(event.getFile());
  }

  @Override
  public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
    updateFileState(event.getFile());
  }

  @Override
  public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
    updateFileState(event.getFile());
  }

  @Override
  public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
    updateFileState(event.getFile());
  }

  private void updateFileState(@Nullable PsiFile psiFile) {
    VirtualFile changedFile = PsiUtilCore.getVirtualFile(psiFile);
    if (changedFile == null) return;
    Editor[] editors = EditorFactory.getInstance().getAllEditors();
    ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(myProject);
    for (Editor editor : editors) {
      EditorImpl editorImpl = tryCast(editor, EditorImpl.class);
      if (editorImpl == null || (!editorImpl.getContentComponent().isShowing() && !ApplicationManager.getApplication().isUnitTestMode())) continue;
      VirtualFile editorFile = editorImpl.getVirtualFile();
      if (editorFile == null) {
        DocumentEx document = editorImpl.getDocument();
        editorFile = FileDocumentManager.getInstance().getFile(document);
      }
      if (editorFile == null || changedFile.equals(editorFile) || !fileIndex.isInContent(editorFile)) continue;
      PsiJavaFile psiJavaFile = getJavaFile(myProject, editorFile);
      if (psiJavaFile == null) continue;
      FileStateUpdater.setPreviousState(psiJavaFile);
    }
  }

  private static @Nullable PsiJavaFile getJavaFile(@NotNull Project project, @Nullable VirtualFile file) {
    if (file == null || file instanceof VirtualFileWindow || !file.isValid()) return null;
    if (!ProjectProblemUtils.containsJvmLanguage(file)) return null;
    return tryCast(PsiManager.getInstance(project).findFile(file), PsiJavaFile.class);
  }

  private static @Nullable Editor getEditor(@Nullable FileEditor fileEditor) {
    TextEditor textEditor = tryCast(fileEditor, TextEditor.class);
    return textEditor == null ? null : textEditor.getEditor();
  }

  static class MyStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
      if (ApplicationManager.getApplication().isHeadlessEnvironment() && !TestModeFlags.is(ProjectProblemUtils.ourTestingProjectProblems)) {
        return;
      }

      ProjectProblemFileSelectionListener listener = new ProjectProblemFileSelectionListener(project);
      MessageBusConnection connection = project.getMessageBus().connect();
      connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);
      connection.subscribe(InlayHintsSettings.getINLAY_SETTINGS_CHANGED(), listener);
      connection.subscribe(VirtualFileManager.VFS_CHANGES, listener);
      connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, listener);
      PsiManager.getInstance(project).addPsiTreeChangeListener(listener, FileStateCache.getInstance(project));
    }
  }
}
