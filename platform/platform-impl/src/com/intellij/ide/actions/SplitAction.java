// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class SplitAction extends AnAction implements DumbAware {
  public static final Key<Boolean> FORBID_TAB_SPLIT = new Key<>("FORBID_TAB_SPLIT");
  private final int myOrientation;
  private final boolean myCloseSource;

  protected SplitAction(final int orientation) {
    this(orientation, false);
  }

  protected SplitAction(final int orientation, boolean closeSource) {
    myOrientation = orientation;
    myCloseSource = closeSource;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent event) {
    final EditorWindow window = event.getRequiredData(EditorWindow.DATA_KEY);
    final VirtualFile file = window.getSelectedFile();

    if (myCloseSource && file != null) {
      file.putUserData(EditorWindow.Companion.getDRAG_START_PINNED_KEY$intellij_platform_ide_impl(), window.isFilePinned(file));
      window.closeFile(file, false, false);
    }

    window.split(myOrientation, true, file, true);
  }

  @Override
  public void update(@NotNull final AnActionEvent event) {
    EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    VirtualFile selectedFile = window != null ? window.getSelectedFile() : null;

    boolean enabled = isEnabled(selectedFile, window);
    event.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private boolean isEnabled(@Nullable VirtualFile vFile, @Nullable EditorWindow window) {
    if (vFile == null || window == null) {
      return false;
    }
    if (!myCloseSource && FileEditorManagerImpl.forbidSplitFor(vFile)) {
      return false;
    }
    int minimum = myCloseSource ? 2 : 1;
    return window.getTabCount() >= minimum;
  }
}
