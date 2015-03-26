/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class ToggleBookmarkAction extends BookmarksAction implements DumbAware {
  public ToggleBookmarkAction() {
    getTemplatePresentation().setText(IdeBundle.message("action.bookmark.toggle"));
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    DataContext dataContext = event.getDataContext();
    event.getPresentation().setEnabled(project != null &&
                                       (ToolWindowManager.getInstance(project).isEditorComponentActive() &&
                                        CommonDataKeys.EDITOR.getData(dataContext) != null ||
                                        CommonDataKeys.VIRTUAL_FILE.getData(dataContext) != null));

    event.getPresentation().setText(IdeBundle.message("action.bookmark.toggle"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    BookmarkInContextInfo info = new BookmarkInContextInfo(e.getDataContext(), project).invoke();
    if (info.getFile() == null) return;

    if (info.getBookmarkAtPlace() != null) {
      BookmarkManager.getInstance(project).removeBookmark(info.getBookmarkAtPlace());
    }
    else {
      BookmarkManager.getInstance(project).addTextBookmark(info.getFile(), info.getLine(), "");
    }
  }
}
