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
package com.intellij.formatting;

import com.intellij.ide.bookmarks.Bookmark;
import com.intellij.ide.bookmarks.BookmarkManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.diff.Diff;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * There is a possible case that we understand that formatting introduces big number of changes to the underlying document.
 * That number may be big enough for that their subsequent appliance is much slower than building resulting text directly
 * and replacing the whole document text.
 * <p/>
 * Current class defines a contract for a listener interested in such situations.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 12/23/10 11:38 AM
 */
public class BulkReformatListener {

  private final List<BookmarkData> myBookmarks = new ArrayList<BookmarkData>();

  private final Project     myProject;
  private final VirtualFile myVirtualFile;

  public BulkReformatListener(@NotNull Project project, VirtualFile virtualFile) {
    myProject = project;
    myVirtualFile = virtualFile;
  }

  /**
   * Is expected to be called before bulk processing.
   * 
   * @param document    document which text is about to be reformatted at bulk mode
   * @param newText     formatted text that is about to replace the text at the given document 
   */
  public void beforeProcessing(@NotNull Document document, @NotNull CharSequence newText) {
    reset();

    Diff.Change change = Diff.buildChanges(document.getCharsSequence(), newText);
    if (change == null) {
      return;
    }
    BookmarkManager manager = BookmarkManager.getInstance(myProject);
    for (Bookmark bookmark : manager.getValidBookmarks()) {
      if (bookmark.isValid() && bookmark.getDocument() == document) {
        int newLine = Diff.translateLine(change, bookmark.getLine());
        if (newLine >= 0) {
          myBookmarks.add(new BookmarkData(bookmark.getDescription(), bookmark.getLine()));
        }
      }
    }
  }

  /**
   * Is expected to be called just after bulk reformat processing.
   */
  public void afterProcessing() {
    if (myBookmarks.isEmpty()) {
      return;
    }

    final BookmarkManager manager = BookmarkManager.getInstance(myProject);
    for (BookmarkData bookmark : myBookmarks) {
      manager.addTextBookmark(myVirtualFile, bookmark.line, bookmark.description);
    }
    
    reset();
  }
  
  private void reset() {
    myBookmarks.clear();
  }
  
  private static class BookmarkData {
    public final String description;
    public final int    line;

    BookmarkData(String description, int line) {
      this.description = description;
      this.line = line;
    }
  }
}
