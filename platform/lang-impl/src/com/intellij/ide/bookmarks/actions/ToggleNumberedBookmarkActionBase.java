/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;

public abstract class ToggleNumberedBookmarkActionBase extends AnAction implements DumbAware {
  private int myNumber;

  public ToggleNumberedBookmarkActionBase(int n) {
    myNumber = n;
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    e.getPresentation().setEnabled(project != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);

    final BookmarkManager manager = BookmarkManager.getInstance(project);
    BookmarksAction.BookmarkInContextInfo info = new BookmarksAction.BookmarkInContextInfo(dataContext, project).invoke();
    final Bookmark oldBookmark = info.getBookmarkAtPlace();

    if (oldBookmark != null) {
      manager.removeBookmark(oldBookmark);
    }

    if (oldBookmark == null || oldBookmark.getMnemonic() != '0' + myNumber) {
      final Bookmark bookmark = BookmarkManager.getInstance(project).addTextBookmark(info.getFile(), info.getLine(), "");
      bookmark.setMnemonic((char)('0' + myNumber));
    }
  }
}
