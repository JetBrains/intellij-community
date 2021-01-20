// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkBundle;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.ide.bookmarks.BookmarkType;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.ui.popup.PopupFactoryImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated use {@link ChooseBookmarkTypeAction} instead
 */
@Deprecated
public class ToggleBookmarkWithMnemonicAction extends ToggleBookmarkAction {
  private boolean myPopupShown;

  public ToggleBookmarkWithMnemonicAction() {
    getTemplatePresentation().setText(BookmarkBundle.messagePointer("action.bookmark.toggle.mnemonic"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(!myPopupShown);

    final BookmarkInContextInfo info = getBookmarkInfo(e);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      if (info != null && info.getBookmarkAtPlace() != null) {
        e.getPresentation().setVisible(false);
      }
      else {
        e.getPresentation().setText(BookmarkBundle.messagePointer("action.presentation.ToggleBookmarkWithMnemonicAction.text"));
      }
    }
    else {
      e.getPresentation().setText(BookmarkBundle.messagePointer("action.bookmark.toggle.mnemonic"));
    }
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
      final Editor editor = e.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        Integer gutterLineAtCursor = e.getData(EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR);
        if (gutterLineAtCursor != null) {
          VisualPosition position = editor.logicalToVisualPosition(new LogicalPosition(gutterLineAtCursor, 0));
          editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, position);
        }
      }

      JBPopup popup = new MnemonicChooser(bookmarks, BookmarkType.get(bookmark.getMnemonic())) {
        @Override
        protected void onChosen(@NotNull BookmarkType type) {
          char c = type.getMnemonic();
          super.onChosen(type);
          bookmarks.setMnemonic(bookmark, c);
        }

        @Override
        protected void onCancelled() {
          super.onCancelled();
          bookmarks.removeBookmark(bookmark);
        }
      }.createPopup(false);

      popup.addListener(new JBPopupListener() {
        @Override
        public void beforeShown(@NotNull LightweightWindowEvent event) {
          myPopupShown = true;
        }

        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          myPopupShown = false;
          if (editor != null) {
            editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, null);
          }
        }
      });

      popup.showInBestPositionFor(e.getDataContext());
      myPopupShown = true;
    }
  }
}
