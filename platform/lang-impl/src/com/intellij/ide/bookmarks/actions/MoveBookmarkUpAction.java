// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.bookmarks.BookmarkBundle;
import com.intellij.ide.bookmarks.BookmarkItem;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ListUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

class MoveBookmarkUpAction extends DumbAwareAction {
  private final Project myProject;
  private final JList<BookmarkItem> myList;

  MoveBookmarkUpAction(Project project, JList<BookmarkItem> list) {
    super(BookmarkBundle.message("action.bookmark.move.up"), null, AllIcons.Actions.MoveUp);
    setEnabledInModalContext(true);
    myProject = project;
    myList = list;
    registerCustomShortcutSet(CommonShortcuts.MOVE_UP, list);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(
      !UISettings.getInstance().getSortBookmarks() &&
      BookmarksAction.notFiltered(myList)
      && BookmarksAction.getSelectedBookmarks(myList).size() == 1
      && myList.getSelectedIndex() > 0);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ListUtil.moveSelectedItemsUp(myList);
    BookmarkManager.getInstance(myProject).moveBookmarkUp(BookmarksAction.getSelectedBookmarks(myList).get(0));
  }
}