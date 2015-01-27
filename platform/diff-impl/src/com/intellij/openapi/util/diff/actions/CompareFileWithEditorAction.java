/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.util.diff.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.diff.impl.DiffRequestFactory;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompareFileWithEditorAction extends BaseShowDiffAction {
  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    VirtualFile selectedFile = getSelectedFile(e);
    if (selectedFile == null) {
      return false;
    }

    VirtualFile currentFile = getEditingFile(e);
    if (currentFile == null) {
      return false;
    }

    if (!canCompare(selectedFile, currentFile)) {
      return false;
    }

    return true;
  }

  @Nullable
  private static VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
    VirtualFile[] array = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (array == null || array.length != 1 || array[0].isDirectory()) {
      return null;
    }

    return array[0];
  }

  @Nullable
  private static VirtualFile getEditingFile(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return null;

    VirtualFile[] selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles();
    if (selectedFiles.length == 0) return null;

    return selectedFiles[0];
  }

  private static boolean canCompare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return file1.isValid() && file2.isValid() && !file1.equals(file2);
  }

  @Nullable
  @Override
  protected DiffRequest getDiffRequest(@NotNull AnActionEvent e) {
    Project project = e.getProject();

    VirtualFile selectedFile = getSelectedFile(e);
    VirtualFile currentFile = getEditingFile(e);

    assert selectedFile != null && currentFile != null;

    return DiffRequestFactory.createFromFile(project, selectedFile, currentFile);
  }
}
