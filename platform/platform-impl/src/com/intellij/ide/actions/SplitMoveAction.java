// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class SplitMoveAction extends SplitAction {

  protected SplitMoveAction(int orientation) {
    super(orientation);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent event) {
    final EditorWindow window = event.getRequiredData(EditorWindow.DATA_KEY);
    final VirtualFile file = window.getSelectedFile();

    if (file != null) {
      file.putUserData(EditorWindow.DRAG_START_PINNED_KEY, window.isFilePinned(file));
      file.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, Boolean.TRUE);

      window.closeFile(file, true, false);
      window.split(myOrientation, true, file, true);

      file.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null);
    }
  }

  @Override
  protected boolean isEnabled(@NotNull VirtualFile vFile, @NotNull EditorWindow window) {
    return window.getTabCount() >= 2;
  }
}
