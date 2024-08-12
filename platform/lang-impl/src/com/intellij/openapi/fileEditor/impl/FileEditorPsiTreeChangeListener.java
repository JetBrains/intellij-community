// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Updates attribute of open files when roots change
 */
public final class FileEditorPsiTreeChangeListener extends PsiTreeChangeAdapter {
  private static final Logger LOG = Logger.getInstance(FileEditorPsiTreeChangeListener.class);
  private final Project myProject;

  public FileEditorPsiTreeChangeListener(Project project) {
    myProject = project;
    if (myProject.isDefault()) throw ExtensionNotApplicableException.create();
  }

  @Override
  public void propertyChanged(final @NotNull PsiTreeChangeEvent e) {
    if (PsiTreeChangeEvent.PROP_ROOTS.equals(e.getPropertyName())) {
      ApplicationManager.getApplication().assertWriteIntentLockAcquired();
      FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
      final VirtualFile[] openFiles = fileEditorManager.getOpenFiles();
      for (int i = openFiles.length - 1; i >= 0; i--) {
        final VirtualFile file = openFiles[i];
        LOG.assertTrue(file != null);
        fileEditorManager.updateFilePresentation(file);
      }
    }
  }

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
    doChange(event);
  }

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    doChange(event);
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    doChange(event);
  }

  @Override
  public void childMoved(@NotNull PsiTreeChangeEvent event) {
    doChange(event);
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    doChange(event);
  }

  private void doChange(final PsiTreeChangeEvent event) {
    final PsiFile psiFile = event.getFile();
    if (psiFile == null) return;
    VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return;
    FileEditorManagerEx fileEditorManager = (FileEditorManagerEx)FileEditorManager.getInstance(myProject);
    @NotNull @Unmodifiable List<@NotNull FileEditor> editors = fileEditorManager.getAllEditorList(file);
    if (editors.isEmpty()) {
      return;
    }

    final VirtualFile currentFile = fileEditorManager.getCurrentFile();
    if (currentFile != null && Comparing.equal(psiFile.getVirtualFile(), currentFile)) {
      fileEditorManager.updateFilePresentation(currentFile);
    }
  }
}
