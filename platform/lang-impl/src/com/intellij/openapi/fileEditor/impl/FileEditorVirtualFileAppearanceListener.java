// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.ui.VirtualFileAppearanceListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class FileEditorVirtualFileAppearanceListener implements VirtualFileAppearanceListener {
  private final @NotNull Project myProject;

  public FileEditorVirtualFileAppearanceListener(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void virtualFileAppearanceChanged(@NotNull VirtualFile virtualFile) {
    FileEditorManagerEx fileEditorManager = (FileEditorManagerEx)FileEditorManager.getInstance(myProject);
    final VirtualFile currentFile = fileEditorManager.getCurrentFile();
    if (Objects.equals(virtualFile, currentFile)) {
      fileEditorManager.updateFilePresentation(currentFile);
    }
  }
}