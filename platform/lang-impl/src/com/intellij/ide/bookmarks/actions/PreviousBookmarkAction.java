package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.ide.bookmarks.EditorBookmark;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

public class PreviousBookmarkAction extends GotoBookmarkActionBase {
  protected EditorBookmark getBookmarkToGo(Project project, Editor editor) {
    return BookmarkManager.getInstance(project).getPreviousBookmark(editor, true);
  }
}