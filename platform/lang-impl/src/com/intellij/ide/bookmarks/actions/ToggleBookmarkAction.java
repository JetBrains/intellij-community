package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public class ToggleBookmarkAction extends BookmarksAction implements DumbAware {
  public ToggleBookmarkAction() {
    getTemplatePresentation().setText(IdeBundle.message("action.toggle.bookmark"));
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    BookmarkInContextInfo info = new BookmarkInContextInfo(dataContext, project).invoke();

    if (info.getBookmarkAtPlace() != null) {
      new RemoveBookmarkItem(info.getBookmarkAtPlace()).execute(project);
    }
    else {
      new SetBookmarkItem(info.getFile(), info.getLine()).execute(project);
    }
  }

  public void update(AnActionEvent event) {
    super.update(event);

    event.getPresentation().setText(IdeBundle.message("action.toggle.bookmark"));
  }
}
