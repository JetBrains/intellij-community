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

package com.intellij.ide.bookmarks;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.List;

@State(
  name = "BookmarkManager",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public class BookmarkManager extends AbstractProjectComponent implements PersistentStateComponent<Element> {
  private static final int MAX_AUTO_DESCRIPTION_SIZE = 50;

  private final List<Bookmark> myBookmarks = new ArrayList<Bookmark>();

  private final MessageBus myBus;

  public static BookmarkManager getInstance(Project project) {
    return project.getComponent(BookmarkManager.class);
  }

  public BookmarkManager(Project project,
                         MessageBus bus,
                         PsiDocumentManager documentManager,
                         EditorColorsManager colorsManager,
                         EditorFactory editorFactory) {
    super(project);
    colorsManager.addEditorColorsListener(new EditorColorsListener() {
      @Override
      public void globalSchemeChange(EditorColorsScheme scheme) {
        colorsChanged();
      }
    }, project);
    myBus = bus;
    EditorEventMulticaster multicaster = editorFactory.getEventMulticaster();
    multicaster.addDocumentListener(new MyDocumentListener(), myProject);
    multicaster.addEditorMouseListener(new MyEditorMouseListener(), myProject);

    documentManager.addListener(new PsiDocumentManager.Listener() {
      @Override
      public void documentCreated(@NotNull final Document document, PsiFile psiFile) {
        final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file == null) return;
        for (final Bookmark bookmark : myBookmarks) {
          if (Comparing.equal(bookmark.getFile(), file)) {
            UIUtil.invokeLaterIfNeeded(new Runnable() {
              @Override
              public void run() {
                if (myProject.isDisposed()) return;
                bookmark.createHighlighter((MarkupModelEx)DocumentMarkupModel.forDocument(document, myProject, true));
              }
            });
          }
        }
      }

      @Override
      public void fileCreated(@NotNull PsiFile file, @NotNull Document document) {
      }
    });
  }

  public void editDescription(@NotNull Bookmark bookmark) {
    String description = Messages
      .showInputDialog(myProject, IdeBundle.message("action.bookmark.edit.description.dialog.message"),
                       IdeBundle.message("action.bookmark.edit.description.dialog.title"), Messages.getQuestionIcon(),
                       bookmark.getDescription(), new InputValidator() {
        @Override
        public boolean checkInput(String inputString) {
          return true;
        }

        @Override
        public boolean canClose(String inputString) {
          return true;
        }
      });
    if (description != null) {
      setDescription(bookmark, description);
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "BookmarkManager";
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
    myBookmarks.add(0, b);
    myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkAdded(b);
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

    Bookmark b = new Bookmark(myProject, file, -1, description);
    myBookmarks.add(0, b);
    myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkAdded(b);
    return b;
  }


  @NotNull
  public List<Bookmark> getValidBookmarks() {
    List<Bookmark> answer = new ArrayList<Bookmark>();
    for (Bookmark bookmark : myBookmarks) {
      if (bookmark.isValid()) answer.add(bookmark);
    }
    Collections.sort(answer);
    return answer;
  }


  @Nullable
  public Bookmark findEditorBookmark(@NotNull Document document, int line) {
    for (Bookmark bookmark : myBookmarks) {
      if (bookmark.getDocument() == document && bookmark.getLine() == line) {
        return bookmark;
      }
    }

    return null;
  }

  @Nullable
  public Bookmark findFileBookmark(@NotNull VirtualFile file) {
    for (Bookmark bookmark : myBookmarks) {
      if (Comparing.equal(bookmark.getFile(), file) && bookmark.getLine() == -1) return bookmark;
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

  public void removeBookmark(@NotNull Bookmark bookmark) {
    if (myBookmarks.remove(bookmark)) {
      bookmark.release();
      myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkRemoved(bookmark);
    }
  }

  @Override
  public Element getState() {
    Element container = new Element("BookmarkManager");
    writeExternal(container);
    return container;
  }

  @Override
  public void loadState(final Element state) {
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new DumbAwareRunnable() {
      @Override
      public void run() {
        BookmarksListener publisher = myBus.syncPublisher(BookmarksListener.TOPIC);
        for (Bookmark bookmark : myBookmarks) {
          bookmark.release();
          publisher.bookmarkRemoved(bookmark);
        }
        myBookmarks.clear();

        readExternal(state);
      }
    });
  }

  private void readExternal(Element element) {
    for (final Object o : element.getChildren()) {
      Element bookmarkElement = (Element)o;

      if ("bookmark".equals(bookmarkElement.getName())) {
        String url = bookmarkElement.getAttributeValue("url");
        String line = bookmarkElement.getAttributeValue("line");
        String description = StringUtil.notNullize(bookmarkElement.getAttributeValue("description"));
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
      if (!bookmark.isValid()) continue;
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

  @Nullable
  public Bookmark getNextBookmark(@NotNull Editor editor, boolean isWrapped) {
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
  public Bookmark getPreviousBookmark(@NotNull Editor editor, boolean isWrapped) {
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

  @NotNull
  private Bookmark[] getBookmarksForDocument(@NotNull Document document) {
    ArrayList<Bookmark> answer = new ArrayList<Bookmark>();
    for (Bookmark bookmark : getValidBookmarks()) {
      if (document.equals(bookmark.getDocument())) {
        answer.add(bookmark);
      }
    }

    Bookmark[] bookmarks = answer.toArray(new Bookmark[answer.size()]);
    Arrays.sort(bookmarks, new Comparator<Bookmark>() {
      @Override
      public int compare(final Bookmark o1, final Bookmark o2) {
        return o1.getLine() - o2.getLine();
      }
    });
    return bookmarks;
  }

  public void setMnemonic(@NotNull Bookmark bookmark, char c) {
    final Bookmark old = findBookmarkForMnemonic(c);
    if (old != null) removeBookmark(old);

    bookmark.setMnemonic(c);
    myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkChanged(bookmark);
  }

  public void setDescription(@NotNull Bookmark bookmark, String description) {
    bookmark.setDescription(description);
    myBus.syncPublisher(BookmarksListener.TOPIC).bookmarkChanged(bookmark);
  }

  public void colorsChanged() {
    for (Bookmark bookmark : myBookmarks) {
      bookmark.updateHighlighter();
    }
  }


  private class MyEditorMouseListener extends EditorMouseAdapter {
    @Override
    public void mouseClicked(final EditorMouseEvent e) {
      if (e.getArea() != EditorMouseEventArea.LINE_MARKERS_AREA) return;
      if (e.getMouseEvent().isPopupTrigger()) return;
      if ((e.getMouseEvent().getModifiers() & (SystemInfo.isMac ? InputEvent.META_MASK : InputEvent.CTRL_MASK)) == 0) return;

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

  private class MyDocumentListener extends DocumentAdapter {
    @Override
    public void beforeDocumentChange(DocumentEvent e) {
      if (e.isWholeTextReplaced()) return;
      List<Bookmark> bookmarksToRemove = null;
      for (Bookmark bookmark : myBookmarks) {
        Document document = bookmark.getDocument();
        if (document == null || document != e.getDocument()) continue;
        if (bookmark.getLine() ==-1) continue;

        int start = document.getLineStartOffset(bookmark.getLine());
        int end = document.getLineEndOffset(bookmark.getLine());
        if (start >= e.getOffset() && end <= e.getOffset() + e.getOldLength() ) {
          if (bookmarksToRemove == null) {
            bookmarksToRemove = new ArrayList<Bookmark>();
          }
          bookmarksToRemove.add(bookmark);
        }
      }
      if (bookmarksToRemove != null) {
        for (Bookmark bookmark : bookmarksToRemove) {
          removeBookmark(bookmark);
        }
      }
    }

    private boolean isDuplicate(Bookmark bookmark, @Nullable List<Bookmark> toRemove) {
      for (Bookmark b : myBookmarks) {
        if (b == bookmark) continue;
        if (!b.isValid()) continue;
        if (Comparing.equal(b.getFile(), bookmark.getFile()) && b.getLine() == bookmark.getLine()) {
          if (toRemove == null || !toRemove.contains(b)) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void documentChanged(DocumentEvent e) {
      List<Bookmark> bookmarksToRemove = null;
      for (Bookmark bookmark : myBookmarks) {
        if (!bookmark.isValid() || isDuplicate(bookmark, bookmarksToRemove)) {
          if (bookmarksToRemove == null) {
            bookmarksToRemove = new ArrayList<Bookmark>();
          }
          bookmarksToRemove.add(bookmark);
        }
      }

      if (bookmarksToRemove != null) {
        for (Bookmark bookmark : bookmarksToRemove) {
          removeBookmark(bookmark);
        }
      }
    }
  }
}

