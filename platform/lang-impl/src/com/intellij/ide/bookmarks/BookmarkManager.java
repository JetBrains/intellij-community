/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.bookmarks;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseEventArea;
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBus;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.List;

@State(
  name = "BookmarkManager",
  storages = {
    @Storage(id = "default", file = "$WORKSPACE_FILE$")
  }
)
public class BookmarkManager implements PersistentStateComponent<Element> {
  private static final int MAX_AUTO_DESCRIPTION_SIZE = 50;
  private final List<Bookmark> myBookmarks = new ArrayList<Bookmark>();
  private final MyEditorMouseListener myEditorMouseListener = new MyEditorMouseListener();
  private final Project myProject;
  private final MessageBus myBus;

  public static BookmarkManager getInstance(Project project) {
    return ServiceManager.getService(project, BookmarkManager.class);
  }

  public BookmarkManager(Project project, MessageBus bus) {
    myProject = project;
    myBus = bus;
  }

  public void projectOpened() {
    EditorEventMulticasterEx eventMulticaster = (EditorEventMulticasterEx)EditorFactory.getInstance()
      .getEventMulticaster();
    eventMulticaster.addEditorMouseListener(myEditorMouseListener, myProject);
  }

  public Project getProject() {
    return myProject;
  }

  public void addEditorBookmark(Editor editor, int lineIndex) {
    Document document = editor.getDocument();
    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (psiFile == null) return;

    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return;

    addTextBookmark(virtualFile, lineIndex, getAutoDescription(editor, lineIndex));
  }

  public Bookmark addTextBookmark(VirtualFile file, int lineIndex, String description) {
    Bookmark b = new Bookmark(myProject, file, lineIndex, description);
    myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkAdded(b);
    myBookmarks.add(0, b);
    return b;
  }

  public static String getAutoDescription(final Editor editor, final int lineIndex) {
    String autoDescription = editor.getSelectionModel().getSelectedText();
    if ( autoDescription == null ) {
      Document document = editor.getDocument();
      autoDescription = document.getCharsSequence()
        .subSequence(document.getLineStartOffset(lineIndex), document.getLineEndOffset(lineIndex)).toString().trim();
    }
    if ( autoDescription.length () > MAX_AUTO_DESCRIPTION_SIZE) {
      return autoDescription.substring(0, MAX_AUTO_DESCRIPTION_SIZE)+"...";
    }
    return autoDescription;
  }

  @Nullable
  public Bookmark addFileBookmark(VirtualFile file, String description) {
    if (file == null) return null;
    if (findFileBookmark(file) != null) return null;

    Bookmark b = new Bookmark(myProject, file, description);
    myBookmarks.add(0, b);
    myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkAdded(b);
    return b;
  }


  public List<Bookmark> getValidBookmarks() {
    List<Bookmark> answer = new ArrayList<Bookmark>();
    for (Bookmark bookmark : myBookmarks) {
      if (bookmark.isValid()) answer.add(bookmark);
    }
    return answer;
  }


  @Nullable
  public Bookmark findEditorBookmark(Document document, int lineIndex) {
    for (Bookmark bookmark : myBookmarks) {
      if (bookmark.getDocument() == document && bookmark.getLine() == lineIndex) {
        return bookmark;
      }
    }

    return null;
  }

  @Nullable
  public Bookmark findFileBookmark(VirtualFile file) {
    for (Bookmark bookmark : myBookmarks) {
      if (bookmark.getFile() == file && bookmark.getLine() == -1) return bookmark;
    }

    return null;
  }

  @Nullable
  public Bookmark findBookmarkForMnemonic(char m) {
    final char mm = Character.toUpperCase(m);
    for (Bookmark bookmark : myBookmarks) {
      if (mm == bookmark.getMnemonic()) return bookmark;
    }
    return null;
  }

  public boolean hasBookmarksWithMnemonics() {
    for (Bookmark bookmark : myBookmarks) {
      if (bookmark.getMnemonic() != 0) return true;
    }

    return false;
  }

  public void removeBookmark(Bookmark bookmark) {
    myBookmarks.remove(bookmark);
    bookmark.release();
    myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkRemoved(bookmark);
  }

  public Element getState() {
    Element container = new Element("BookmarkManager");
    writeExternal(container);
    return container;
  }

  public void loadState(Element state) {
    BookmarksListener publisher = myBus.syncPublisher(BookmarksListener.TOPIC);
    for (Bookmark bookmark : myBookmarks) {
      bookmark.release();
      publisher.bookmarkRemoved(bookmark);
    }
    myBookmarks.clear();

    readExternal(state);
  }

  private void readExternal(Element element) {
    for (final Object o : element.getChildren()) {
      Element bookmarkElement = (Element)o;

      if ("bookmark".equals(bookmarkElement.getName())) {
        String url = bookmarkElement.getAttributeValue("url");
        String line = bookmarkElement.getAttributeValue("line");
        String description = bookmarkElement.getAttributeValue("description");
        String mnemonic = bookmarkElement.getAttributeValue("mnemonic");

        Bookmark b = null;
        VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
        if (file != null) {
          if (line != null) {
            try {
              int lineIndex = Integer.parseInt(line);
              b = addTextBookmark(file, lineIndex, description);
            }
            catch (NumberFormatException e) {
              // Ignore. Will miss bookmark if line number cannot be parsed
            }
          }
          else {
            b = addFileBookmark(file, description);
          }
        }

        if (b != null && mnemonic != null && mnemonic.length() == 1) {
          setMnemonic(b, mnemonic.charAt(0));
        }
      }
    }
  }

  private void writeExternal(Element element) {
    List<Bookmark> reversed = new ArrayList<Bookmark>(myBookmarks);
    Collections.reverse(reversed);

    for (Bookmark bookmark : reversed) {
      Element bookmarkElement = new Element("bookmark");

      bookmarkElement.setAttribute("url", bookmark.getFile().getUrl());

      String description = bookmark.getNotEmptyDescription();
      if (description != null) {
        bookmarkElement.setAttribute("description", description);
      }

      int line = bookmark.getLine();
      if (line >= 0) {
        bookmarkElement.setAttribute("line", String.valueOf(line));
      }

      char mnemonic = bookmark.getMnemonic();
      if (mnemonic != 0) {
        bookmarkElement.setAttribute("mnemonic", String.valueOf(mnemonic));
      }

      element.addContent(bookmarkElement);
    }
  }

  /**
   * Try to move bookmark one position up in the list
   *
   * @return bookmark list after moving
   */
  public List<Bookmark> moveBookmarkUp(Bookmark bookmark) {
    int index = myBookmarks.indexOf(bookmark);
    if (index > 0) {
      Collections.swap(myBookmarks, index, index - 1);
    }

    return myBookmarks;
  }


  /**
   * Try to move bookmark one position down in the list
   *
   * @return bookmark list after moving
   */
  public List<Bookmark> moveBookmarkDown(Bookmark bookmark) {
    int index = myBookmarks.indexOf(bookmark);
    if (index < myBookmarks.size() - 1) {
      Collections.swap(myBookmarks, index, index + 1);
    }

    return myBookmarks;
  }

  @Nullable
  public Bookmark getNextBookmark(Editor editor, boolean isWrapped) {
    Bookmark[] bookmarksForDocument = getBookmarksForDocument(editor.getDocument());
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    for (Bookmark bookmark : bookmarksForDocument) {
      if (bookmark.getLine() > lineNumber) return bookmark;
    }
    if (isWrapped && bookmarksForDocument.length > 0) {
      return bookmarksForDocument[0];
    }
    return null;
  }

  @Nullable
  public Bookmark getPreviousBookmark(Editor editor, boolean isWrapped) {
    Bookmark[] bookmarksForDocument = getBookmarksForDocument(editor.getDocument());
    int lineNumber = editor.getCaretModel().getLogicalPosition().line;
    for (int i = bookmarksForDocument.length - 1; i >= 0; i--) {
      Bookmark bookmark = bookmarksForDocument[i];
      if (bookmark.getLine() < lineNumber) return bookmark;
    }
    if (isWrapped && bookmarksForDocument.length > 0) {
      return bookmarksForDocument[bookmarksForDocument.length - 1];
    }
    return null;
  }

  private Bookmark[] getBookmarksForDocument(Document document) {
    ArrayList<Bookmark> answer = new ArrayList<Bookmark>();
    for (Bookmark bookmark : getValidBookmarks()) {
      if (document.equals(bookmark.getDocument())) {
        answer.add(bookmark);
      }
    }

    Bookmark[] bookmarks = answer.toArray(new Bookmark[answer.size()]);
    Arrays.sort(bookmarks, new Comparator<Bookmark>() {
      public int compare(final Bookmark o1, final Bookmark o2) {
        return o1.getLine() - o2.getLine();
      }
    });
    return bookmarks;
  }

  public void setMnemonic(Bookmark bookmark, char c) {
    final Bookmark old = findBookmarkForMnemonic(c);
    if (old != null) removeBookmark(old);

    bookmark.setMnemonic(c);
    myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkChanged(bookmark);
  }

  public void setDescription(Bookmark bookmark, String description) {
    bookmark.setDescription(description);
    myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkChanged(bookmark);
  }


  private class MyEditorMouseListener extends EditorMouseAdapter {
    public void mouseClicked(final EditorMouseEvent e) {
      if (e.getArea() != EditorMouseEventArea.LINE_MARKERS_AREA) return;
      if (e.getMouseEvent().isPopupTrigger()) return;
      if ((e.getMouseEvent().getModifiers() & InputEvent.CTRL_MASK) == 0) return;

      Editor editor = e.getEditor();
      int line = editor.xyToLogicalPosition(new Point(e.getMouseEvent().getX(), e.getMouseEvent().getY())).line;
      if (line < 0) return;

      Document document = editor.getDocument();

      Bookmark bookmark = findEditorBookmark(document, line);
      if (bookmark == null) {
        addEditorBookmark(editor, line);
      }
      else {
        removeBookmark(bookmark);
      }
      e.consume();
    }
  }
}

