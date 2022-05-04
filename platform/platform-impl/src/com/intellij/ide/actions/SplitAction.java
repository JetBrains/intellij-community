// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladimir Kondratyev
 * @author Konstantin Bulenkov
 */
public abstract class SplitAction extends AnAction implements DumbAware {
  public static final Key<Boolean> FORBID_TAB_SPLIT = new Key<>("FORBID_TAB_SPLIT");
  protected final int myOrientation;

  protected SplitAction(final int orientation) {
    myOrientation = orientation;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent event) {
    final EditorWindow window = event.getRequiredData(EditorWindow.DATA_KEY);
    final VirtualFile file = window.getSelectedFile();

    window.split(myOrientation, true, file, true);
  }

  @Override
  public void update(@NotNull final AnActionEvent event) {
    EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    VirtualFile selectedFile = window != null ? window.getSelectedFile() : null;

    boolean enabled = selectedFile != null && isEnabled(selectedFile, window);
    event.getPresentation().setEnabledAndVisible(enabled);
  }

  protected boolean isEnabled(@NotNull VirtualFile vFile, @NotNull EditorWindow window) {
    if (FileEditorManagerImpl.forbidSplitFor(vFile)) {
      return false;
    }
    return window.getTabCount() >= 1;
  }
}
