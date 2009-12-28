/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

abstract class TabNavigationActionBase extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.TabNavigationActionBase");

  private final int myDir;

  TabNavigationActionBase (final int dir) {
    LOG.assertTrue (dir == 1 || dir == -1);
    myDir = dir;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
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

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    presentation.setEnabled(false);
    if (project == null) {
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
    VirtualFile selectedFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    navigateImpl(dataContext, project, selectedFile, myDir);
  }

  public static void navigateImpl(final DataContext dataContext, Project project, VirtualFile selectedFile, final int dir) {
    LOG.assertTrue (dir == 1 || dir == -1);
    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
    EditorWindow currentWindow = EditorWindow.DATA_KEY.getData(dataContext);
    if (currentWindow == null){
      currentWindow = editorManager.getCurrentWindow ();
    }
    final VirtualFile[] files = currentWindow.getFiles();
    int index = ArrayUtil.find(files, selectedFile);
    LOG.assertTrue(index != -1);
    editorManager.openFile(files[(index + files.length + dir) % files.length], true);
  }

}
