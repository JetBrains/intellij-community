package com.intellij.ide.bookmarks.actions;

import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.ide.bookmarks.EditorBookmark;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

public class NextBookmarkAction extends GotoBookmarkActionBase {
  protected EditorBookmark getBookmarkToGo(Project project, Editor editor) {
    return BookmarkManager.getInstance(project).getNextBookmark(editor, true);
  }
}