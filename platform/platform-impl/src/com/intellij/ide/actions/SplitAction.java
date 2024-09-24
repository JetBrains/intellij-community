// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.fileEditor.FileEditorManagerKeys;
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
public abstract class SplitAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  public static final Key<Boolean> FORBID_TAB_SPLIT = FileEditorManagerKeys.FORBID_TAB_SPLIT;

  private final int orientation;
  private final boolean closeSource;

  protected SplitAction(int orientation) {
    this(orientation, false);
  }

  protected SplitAction(int orientation, boolean closeSource) {
    this.orientation = orientation;
    this.closeSource = closeSource;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    if (window == null) {
      return;
    }

    VirtualFile file = window.getContextFile();
    if (closeSource && file != null) {
      file.putUserData(EditorWindow.DRAG_START_PINNED_KEY, window.isFilePinned(file));
      window.closeFile(file, false, false);
    }

    window.split(orientation, true, file, true);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    VirtualFile selectedFile = window == null ? null : window.getContextFile();

    boolean enabled = isEnabled(selectedFile, window);
    event.getPresentation().setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private boolean isEnabled(@Nullable VirtualFile file, @Nullable EditorWindow window) {
    if (file == null || window == null) {
      return false;
    }
    if (!closeSource && FileEditorManagerImpl.forbidSplitFor(file)) {
      return false;
    }
    int minimum = closeSource ? 2 : 1;
    return window.getTabCount() >= minimum;
  }
}
