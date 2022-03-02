// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

abstract class TabNavigationActionBase extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(TabNavigationActionBase.class);

  enum NavigationType {NUM1, NUM2, NUM3, NUM4, NUM5, NUM6, NUM7, NUM8, NUM9, PREV, NEXT, LAST}

  private final NavigationType myNavigationType;

  TabNavigationActionBase (@NotNull NavigationType navigationType) {
    myNavigationType = navigationType;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null || project.isDisposed()) {
      return;
    }

    if (LightEdit.owns(project)) {
      LightEditService.getInstance().navigateToTab(this);
      return;
    }

    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);

    if (windowManager.isEditorComponentActive()) {
      doNavigate(dataContext, project);
      return;
    }

    doNavigate(PlatformDataKeys.NONEMPTY_CONTENT_MANAGER.getData(dataContext));
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);

    if (LightEdit.owns(project)) {
      presentation.setEnabled(LightEditService.getInstance().isTabNavigationAvailable(this));
      return;
    }

    presentation.setEnabled(false);
    if (project == null || project.isDisposed()) {
      return;
    }
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (toolWindowManager.isEditorComponentActive()) {
      final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
      EditorWindow currentWindow = EditorWindow.DATA_KEY.getData(dataContext);
      if (currentWindow == null){
        editorManager.getCurrentWindow ();
      }
      if (currentWindow != null) {
        final List<EditorComposite> composites = currentWindow.getAllComposites();
        switch (myNavigationType) {
          case PREV:
          case NEXT:
            presentation.setEnabled(composites.size() > 1);
            break;
          case LAST:
            int index = composites.indexOf(currentWindow.getSelectedComposite());
            presentation.setEnabled(index < composites.size());
            break;
            default:
              int targetIndex = myNavigationType.ordinal();
              presentation.setEnabled(targetIndex < composites.size());
        }
      }
      return;
    }

    ContentManager contentManager = PlatformDataKeys.NONEMPTY_CONTENT_MANAGER.getData(dataContext);
    presentation.setEnabled(contentManager != null && contentManager.getContentCount() > 1 && contentManager.isSingleSelection());
  }

  private void doNavigate(@Nullable ContentManager contentManager) {
    if (contentManager == null) return;

    Content targetContent = null;
    switch (myNavigationType) {
      case PREV:
        contentManager.selectPreviousContent();
        return;
      case NEXT:
        contentManager.selectNextContent();
        return;
      case LAST: {
        targetContent = contentManager.getContent(contentManager.getContentCount() - 1);
        break;
      }
      default:
        int targetIndex = myNavigationType.ordinal();
        if (contentManager.getContentCount() >= targetIndex + 1) {
          targetContent = contentManager.getContent(targetIndex);
        }
    }
    if (targetContent != null) {
      contentManager.setSelectedContent(targetContent, true);
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
    int targetIndex;
    switch (myNavigationType) {
      case PREV:
        targetIndex = (index + files.length - 1) % files.length;
        break;
      case NEXT:
        targetIndex = (index + files.length + 1) % files.length;
        break;
      case LAST:
        targetIndex = files.length - 1;
        break;
      default:
        targetIndex = myNavigationType.ordinal();
    }
    if (targetIndex < files.length) {
      editorManager.openFile(files[targetIndex], true);
    }
  }

  private static abstract class GoToTabAction extends TabNavigationActionBase {
    protected GoToTabAction(@NotNull NavigationType navigationType) {
      super(navigationType);
      if (navigationType == NavigationType.LAST) {
        getTemplatePresentation().setText(ActionsBundle.messagePointer("action.GoToLastTab.text"));
        getTemplatePresentation().setDescription(ActionsBundle.messagePointer("action.GoToLastTab.description"));
      } else {
        getTemplatePresentation().setText(ActionsBundle.messagePointer("action.GoToTab.text", navigationType.ordinal() + 1));
        getTemplatePresentation().setDescription(ActionsBundle.messagePointer("action.GoToTab.description", navigationType.ordinal() + 1));
      }
    }
  }

  public static final class GoToTab1Action extends GoToTabAction {
    private GoToTab1Action() {
      super(NavigationType.NUM1);
    }
  }

  public static final class GoToTab2Action extends GoToTabAction {
    private GoToTab2Action() {
      super(NavigationType.NUM2);
    }
  }

  public static final class GoToTab3Action extends GoToTabAction {
    private GoToTab3Action() {
      super(NavigationType.NUM3);
    }
  }

  public static final class GoToTab4Action extends GoToTabAction {
    private GoToTab4Action() {
      super(NavigationType.NUM4);
    }
  }

  public static final class GoToTab5Action extends GoToTabAction {
    private GoToTab5Action() {
      super(NavigationType.NUM5);
    }
  }

  public static final class GoToTab6Action extends GoToTabAction {
    private GoToTab6Action() {
      super(NavigationType.NUM6);
    }
  }

  public static final class GoToTab7Action extends GoToTabAction {
    private GoToTab7Action() {
      super(NavigationType.NUM7);
    }
  }

  public static final class GoToTab8Action extends GoToTabAction {
    private GoToTab8Action() {
      super(NavigationType.NUM8);
    }
  }

  public static final class GoToTab9Action extends GoToTabAction {
    private GoToTab9Action() {
      super(NavigationType.NUM9);
    }
  }

  public static class GoToLastTabAction extends GoToTabAction {
    public GoToLastTabAction() {
      super(NavigationType.LAST);
    }
  }
}
