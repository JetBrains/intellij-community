// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladimir Kondratyev
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
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    final EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    final VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);

    fileEditorManager.createSplitter(myOrientation, window);

    if (myCloseSource && window != null && file != null) {
      window.closeFile(file, false, false);
    }
  }

  @Override
  public void update(@NotNull final AnActionEvent event) {
    final Project project = event.getData(CommonDataKeys.PROJECT);
    final EditorWindow window = event.getData(EditorWindow.DATA_KEY);
    boolean isForbidden = window != null && isProhibitionAllowed() && window.getSelectedFile().getUserData(FORBID_TAB_SPLIT) != null;

    if (isForbidden) {
      event.getPresentation().setEnabledAndVisible(false);
    }
    else {
      final int minimum = myCloseSource ? 2 : 1;
      final boolean enabled = project != null
                              && window != null
                              && window.getTabCount() >= minimum;
      event.getPresentation().setEnabledAndVisible(enabled);
    }
  }

  protected boolean isProhibitionAllowed() {
    return false;
  }
}
