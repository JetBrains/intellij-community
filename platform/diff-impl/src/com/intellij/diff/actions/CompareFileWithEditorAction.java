/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.diff.actions;

import com.intellij.diff.DiffRequestFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
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

    EditorWindow window = FileEditorManagerEx.getInstanceEx(project).getCurrentWindow();
    if (window == null) return null;
    EditorWithProviderComposite editor = window.getSelectedEditor(true);
    return editor == null ? null : editor.getFile();
  }

  private static boolean canCompare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return !file1.equals(file2) && hasContent(file1) && hasContent(file2);
  }

  @Nullable
  @Override
  protected DiffRequest getDiffRequest(@NotNull AnActionEvent e) {
    Project project = e.getProject();

    VirtualFile selectedFile = getSelectedFile(e);
    VirtualFile currentFile = getEditingFile(e);

    assert selectedFile != null && currentFile != null;

    ContentDiffRequest request = DiffRequestFactory.getInstance().createFromFiles(project, selectedFile, currentFile);

    DiffContent editorContent = request.getContents().get(1);
    if (editorContent instanceof DocumentContent) {
      Editor[] editors = EditorFactory.getInstance().getEditors(((DocumentContent)editorContent).getDocument());
      if (editors.length != 0) {
        request.putUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, editors[0].getCaretModel().getLogicalPosition().line));
      }
    }

    return request;
  }
}
