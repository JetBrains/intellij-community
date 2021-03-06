// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkBundle;
import com.intellij.ide.bookmarks.BookmarkItem;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ListUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

class DeleteBookmarkAction extends DumbAwareAction {
  private final Project myProject;
  private final JList<? extends BookmarkItem> myList;

  DeleteBookmarkAction(Project project, JList<? extends BookmarkItem> list) {
    super(BookmarkBundle.messagePointer("action.DeleteBookmarkAction.delete.text"),
          BookmarkBundle.messagePointer("action.delete.current.bookmark.description"), AllIcons.General.Remove);
    setEnabledInModalContext(true);
    myProject = project;
    myList = list;
    registerCustomShortcutSet(CustomShortcutSet.fromString("DELETE", "BACK_SPACE"), list);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(BookmarksAction.getSelectedBookmarks(myList).size() > 0);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<Bookmark> bookmarks = BookmarksAction.getSelectedBookmarks(myList);
    ListUtil.removeSelectedItems(myList);

    for (Bookmark bookmark : bookmarks) {
      BookmarkManager.getInstance(myProject).removeBookmark(bookmark);
    }
  }
}