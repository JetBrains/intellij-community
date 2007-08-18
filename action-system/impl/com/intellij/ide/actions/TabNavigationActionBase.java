package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ArrayUtil;

abstract class TabNavigationActionBase extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.TabNavigationActionBase");

  private final int myDir;

  TabNavigationActionBase (final int dir) {
    LOG.assertTrue (dir == 1 || dir == -1);
    myDir = dir;
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }

    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);

    if (windowManager.isEditorComponentActive()) {
      doNavigate(dataContext, project);
      return;
    }

    ContentManager contentManager = (ContentManager)dataContext.getData(DataConstantsEx.CONTENT_MANAGER);
    if (contentManager == null) return;
    doNavigate(contentManager);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    presentation.setEnabled(false);
    if (project == null) {
      return;
    }
    final ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    if (windowManager.isEditorComponentActive()) {
      final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
      EditorWindow currentWindow = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
      if (currentWindow == null){
        editorManager.getCurrentWindow ();
      }
      if (currentWindow != null) {
        final VirtualFile[] files = currentWindow.getFiles();
        presentation.setEnabled(files.length > 1);
      }
      return;
    }

    ContentManager contentManager = (ContentManager)dataContext.getData(DataConstantsEx.CONTENT_MANAGER);
    presentation.setEnabled(contentManager != null && contentManager.getContentCount() > 1);
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
    VirtualFile selectedFile = DataKeys.VIRTUAL_FILE.getData(dataContext);
    navigateImpl(dataContext, project, selectedFile, myDir);
  }

  public static void navigateImpl(final DataContext dataContext, Project project, VirtualFile selectedFile, final int dir) {
    LOG.assertTrue (dir == 1 || dir == -1);
    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
    EditorWindow currentWindow = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (currentWindow == null){
      currentWindow = editorManager.getCurrentWindow ();
    }
    final VirtualFile[] files = currentWindow.getFiles();
    int index = ArrayUtil.find(files, selectedFile);
    LOG.assertTrue(index != -1);
    editorManager.openFile(files[(index + files.length + dir) % files.length], false);
  }

}
