// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions;

import com.intellij.diff.actions.impl.MutableDiffRequestChain;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.Side;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
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

  private static @Nullable VirtualFile getSelectedFile(@NotNull AnActionEvent e) {
    VirtualFile[] array = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (array == null || array.length != 1 || array[0].isDirectory()) {
      return null;
    }

    return array[0];
  }

  private static @Nullable VirtualFile getEditingFile(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return null;

    EditorWindow window = FileEditorManagerEx.getInstanceEx(project).getCurrentWindow();
    if (window == null) return null;
    EditorComposite composite = window.getSelectedComposite(true);
    return composite == null ? null : composite.getFile();
  }

  private static boolean canCompare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return !file1.equals(file2) && hasContent(file1) && hasContent(file2);
  }

  @Override
  protected @NotNull DiffRequestChain getDiffRequestChain(@NotNull AnActionEvent e) {
    Project project = e.getProject();

    VirtualFile selectedFile = getSelectedFile(e);
    VirtualFile currentFile = getEditingFile(e);
    assert selectedFile != null && currentFile != null;

    MutableDiffRequestChain chain = createMutableChainFromFiles(project, selectedFile, currentFile);

    DiffContent editorContent = chain.getContent2();
    if (editorContent instanceof DocumentContent) {
      Editor editor = EditorFactory.getInstance().editors(((DocumentContent)editorContent).getDocument()).findFirst().orElse(null);
      if (editor != null) {
        int currentLine = editor.getCaretModel().getLogicalPosition().line;
        chain.putRequestUserData(DiffUserDataKeys.SCROLL_TO_LINE, Pair.create(Side.RIGHT, currentLine));
      }
    }

    return chain;
  }
}
