/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ListUtil;
import com.intellij.ui.speedSearch.ListWithFilter;

import javax.swing.*;
import java.util.List;

class DeleteBookmarkAction extends DumbAwareAction {
  private final Project myProject;
  private final JList myList;

  DeleteBookmarkAction(Project project, JList list) {
    super("Delete", "Delete current bookmark", AllIcons.General.Remove);
    myProject = project;
    myList = list;
    registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"), list);
  }

  @Override
  public void update(AnActionEvent e) {
    final boolean searchActive = ListWithFilter.isSearchActive(myList);
    e.getPresentation().setEnabled(!searchActive && BookmarksAction.getSelectedBookmarks(myList).size() > 0);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    List<Bookmark> bookmarks = BookmarksAction.getSelectedBookmarks(myList);
    ListUtil.removeSelectedItems(myList);

    for (Bookmark bookmark : bookmarks) {
      BookmarkManager.getInstance(myProject).removeBookmark(bookmark);
    }
  }
}
