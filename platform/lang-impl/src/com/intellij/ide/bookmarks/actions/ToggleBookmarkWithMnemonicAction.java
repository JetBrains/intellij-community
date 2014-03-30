/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.IdeBundle;
import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.util.ui.UIUtil;

public class ToggleBookmarkWithMnemonicAction extends ToggleBookmarkAction {
  public ToggleBookmarkWithMnemonicAction() {
    getTemplatePresentation().setText(IdeBundle.message("action.bookmark.toggle.mnemonic"));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    super.actionPerformed(e);

    DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;
    final BookmarkInContextInfo info = new BookmarkInContextInfo(dataContext, project).invoke();
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

      final ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(mc, mc);
      popup[0] = builder.
        setTitle("Bookmark Mnemonic").
        setFocusable(true).
        setRequestFocus(true).
        setMovable(false).
        setCancelKeyEnabled(false).
        setAdText(bookmarks.hasBookmarksWithMnemonics() ? (UIUtil.isUnderDarcula() ? "Brown" : "Yellow") + " cells are in use" : null).
        setResizable(false)
          .createPopup();

      popup[0].showInBestPositionFor(dataContext);
    }
  }

  @Override
  public void update(AnActionEvent event) {
    super.update(event);

    event.getPresentation().setText(IdeBundle.message("action.bookmark.toggle.mnemonic"));
  }
}
