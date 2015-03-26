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
import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ToggleBookmarkWithMnemonicAction extends ToggleBookmarkAction {
  public ToggleBookmarkWithMnemonicAction() {
    getTemplatePresentation().setText(IdeBundle.message("action.bookmark.toggle.mnemonic"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(IdeBundle.message("action.bookmark.toggle.mnemonic"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    super.actionPerformed(e);

    final Project project = e.getProject();
    if (project == null) return;

    final BookmarkInContextInfo info = new BookmarkInContextInfo(e.getDataContext(), project).invoke();
    final Bookmark bookmark = info.getBookmarkAtPlace();
    final BookmarkManager bookmarks = BookmarkManager.getInstance(project);
    if (bookmark != null) {
      final JBPopup[] popup = new JBPopup[1];

      MnemonicChooser mc = new MnemonicChooser() {
        @Override
        protected void onMnemonicChosen(char c) {
          popup[0].cancel();
          bookmarks.setMnemonic(bookmark, c);
        }

        @Override
        protected void onCancelled() {
          popup[0].cancel();
          bookmarks.removeBookmark(bookmark);
        }

        @Override
        protected boolean isOccupied(char c) {
          return bookmarks.findBookmarkForMnemonic(c) != null;
        }
      };

      popup[0] = JBPopupFactory.getInstance().createComponentPopupBuilder(mc, mc).
        setTitle("Bookmark Mnemonic").
        setFocusable(true).
        setRequestFocus(true).
        setMovable(false).
        setCancelKeyEnabled(false).
        setAdText(bookmarks.hasBookmarksWithMnemonics() ? (UIUtil.isUnderDarcula() ? "Brown" : "Yellow") + " cells are in use" : null).
        setResizable(false).
        createPopup();

      popup[0].showInBestPositionFor(e.getDataContext());
    }
  }
}
