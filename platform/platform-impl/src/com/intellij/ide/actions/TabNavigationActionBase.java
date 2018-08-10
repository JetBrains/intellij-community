// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

abstract class TabNavigationActionBase extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.TabNavigationActionBase");

  private final int myDir;

  TabNavigationActionBase (final int dir) {
    LOG.assertTrue (dir == 1 || dir == -1);
    myDir = dir;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null || project.isDisposed()) {
      return;
    }

    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);

    if (windowManager.isEditorComponentActive()) {
      doNavigate(dataContext, project);
      return;
    }

    ContentManager contentManager = PlatformDataKeys.NONEMPTY_CONTENT_MANAGER.getData(dataContext);
    if (contentManager == null) return;
    doNavigate(contentManager);
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    presentation.setEnabled(false);
    if (project == null || project.isDisposed()) {
      return;
    }
    final ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    if (windowManager.isEditorComponentActive()) {
      final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
      EditorWindow currentWindow = EditorWindow.DATA_KEY.getData(dataContext);
      if (currentWindow == null){
        editorManager.getCurrentWindow ();
      }
      if (currentWindow != null) {
        final VirtualFile[] files = currentWindow.getFiles();
        presentation.setEnabled(files.length > 1);
      }
      return;
    }

    ContentManager contentManager = PlatformDataKeys.NONEMPTY_CONTENT_MANAGER.getData(dataContext);
    presentation.setEnabled(contentManager != null && contentManager.getContentCount() > 1 && contentManager.isSingleSelection());
  }

  private void doNavigate(ContentManager contentManager) {
    if (myDir == -1) {
      contentManager.selectPreviousContent();
    }
    else {
      contentManager.selectNextContent();
    }
  }

  private void doNavigate(DataContext dataContext, Project project) {
    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
    EditorWindow currentWindow = EditorWindow.DATA_KEY.getData(dataContext);
    if (currentWindow == null){
      currentWindow = editorManager.getCurrentWindow ();
    }
    VirtualFile selectedFile = currentWindow.getSelectedFile();
    if (selectedFile == null) {
      selectedFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    }
    final VirtualFile[] files = currentWindow.getFiles();
    int index = ArrayUtil.find(files, selectedFile);
    LOG.assertTrue(index != -1);
    editorManager.openFile(files[(index + files.length + myDir) % files.length], true);
  }
}
