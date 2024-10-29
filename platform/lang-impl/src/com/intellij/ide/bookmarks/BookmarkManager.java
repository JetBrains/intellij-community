// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmarks;

import com.intellij.ide.bookmark.BookmarkType;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @deprecated Please use the new bookmarks manager {@link com.intellij.ide.bookmark.BookmarksManager}.
 */
@State(name = "BookmarkManager", storages = @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE))
@Deprecated
public final class BookmarkManager implements PersistentStateComponent<Element> {
  private record BookmarkInfo(Bookmark bookmark, int line, String text) {
  }

  private record DeletedDocumentBookmarkKey(VirtualFile file, int line, String text) {
  }

  private static final Logger LOG = Logger.getInstance(BookmarkManager.class);
  private static final int MAX_AUTO_DESCRIPTION_SIZE = 50;
  private final MultiMap<VirtualFile, Bookmark> myBookmarks = MultiMap.createConcurrentSet();
  private final Map<DeletedDocumentBookmarkKey, Bookmark> myDeletedDocumentBookmarks = new HashMap<>();
  private final Map<Document, List<BookmarkInfo>> myBeforeChangeData = new HashMap<>();

  private final Project myProject;

  private boolean mySortedState;
  private final AtomicReference<List<Bookmark>> myPendingState = new AtomicReference<>();

  public static BookmarkManager getInstance(@NotNull Project project) {
    return project.getService(BookmarkManager.class);
  }

  public BookmarkManager(@NotNull Project project) {
    myProject = project;
    MessageBusConnection connection = project.getMessageBus().connect();
    connection.subscribe(EditorColorsManager.TOPIC, __ -> colorsChanged());
    EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();
    multicaster.addDocumentListener(new MyDocumentListener(), myProject);

    mySortedState = UISettings.getInstance().getSortBookmarks();
    connection.subscribe(UISettingsListener.TOPIC, uiSettings -> {
      if (mySortedState != uiSettings.getSortBookmarks()) {
        mySortedState = uiSettings.getSortBookmarks();
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!project.isDisposed()) {
            project.getMessageBus().syncPublisher(BookmarksListener.TOPIC).bookmarksOrderChanged();
          }
        });
      }
    });
  }

  public @NotNull Bookmark addTextBookmark(@NotNull VirtualFile file, int lineIndex, @NotNull @NlsSafe String description) {
    ThreadingAssertions.assertEventDispatchThread();

    Bookmark b = new Bookmark(myProject, file, lineIndex, description);
    // increment all other indices and put new bookmark at index 0
    myBookmarks.values().forEach(bookmark -> bookmark.index++);
    myBookmarks.putValue(file, b);
    getPublisher().bookmarkAdded(b);
    return b;
  }

  private @NotNull BookmarksListener getPublisher() {
    return myProject.getMessageBus().syncPublisher(BookmarksListener.TOPIC);
  }

  @TestOnly
  public void addFileBookmark(@NotNull VirtualFile file, @NotNull @NlsSafe String description) {
    if (findFileBookmark(file) != null) {
      return;
    }
    addTextBookmark(file, -1, description);
  }

  private static @NotNull String getAutoDescription(final @NotNull Editor editor, final int lineIndex) {
    String autoDescription = editor.getSelectionModel().getSelectedText();
    if (autoDescription == null) {
      Document document = editor.getDocument();
      autoDescription = document.getCharsSequence()
        .subSequence(document.getLineStartOffset(lineIndex), document.getLineEndOffset(lineIndex)).toString().trim();
    }
    if (autoDescription.length() > MAX_AUTO_DESCRIPTION_SIZE) {
      return autoDescription.substring(0, MAX_AUTO_DESCRIPTION_SIZE) + "...";
    }
    return autoDescription;
  }


  public @NotNull @Unmodifiable List<Bookmark> getValidBookmarks() {
    List<Bookmark> answer = ContainerUtil.filter(myBookmarks.values(), b -> b.isValid());
    if (UISettings.getInstance().getSortBookmarks()) {
      return ContainerUtil.sorted(answer);
    }
    else {
      return ContainerUtil.sorted(answer, Comparator.comparingInt(b -> b.index));
    }
  }

  public @NotNull Collection<Bookmark> getAllBookmarks() {
    return myBookmarks.values();
  }

  public @NotNull Collection<Bookmark> getFileBookmarks(@Nullable VirtualFile file) {
    return myBookmarks.get(file);
  }

  public @Nullable Bookmark findEditorBookmark(@NotNull Document document, int line) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null) return null;
    return findBookmark(file, line);
  }

  @ApiStatus.Internal
  public @Nullable Bookmark findBookmark(@NotNull VirtualFile file, int line) {
    return ContainerUtil.find(myBookmarks.get(file), bookmark -> bookmark.getLine() == line);
  }

  public @Nullable Bookmark findFileBookmark(@NotNull VirtualFile file) {
    return findBookmark(file, -1);
  }

  public @Nullable Bookmark findBookmarkForMnemonic(char m) {
    final char mm = Character.toUpperCase(m);
    return ContainerUtil.find(myBookmarks.values(), bookmark -> bookmark.getMnemonic() == mm);
  }

  public boolean hasBookmarksWithMnemonics() {
    return ContainerUtil.or(myBookmarks.values(), bookmark -> bookmark.getMnemonic() != 0);
  }

  public void removeBookmark(@NotNull Bookmark bookmark) {
    ThreadingAssertions.assertEventDispatchThread();
    VirtualFile file = bookmark.getFile();
    if (myBookmarks.remove(file, bookmark)) {
      int index = bookmark.index;
      // decrement all other indices to maintain them monotonic
      myBookmarks.values().forEach(b -> b.index -= b.index > index ? 1 : 0);
      bookmark.release();
      getPublisher().bookmarkRemoved(bookmark);
    }
  }

  public @Nullable Bookmark findElementBookmark(@NotNull PsiElement element) {
    if (!(element instanceof PsiNameIdentifierOwner) || !element.isValid()) return null;
    if (element instanceof PsiCompiledElement) return null;

    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    PsiElement nameIdentifier = virtualFile == null ? null : ((PsiNameIdentifierOwner)element).getNameIdentifier();
    TextRange nameRange = nameIdentifier == null ? null : nameIdentifier.getTextRange();
    Document document = nameRange == null ? null : FileDocumentManager.getInstance().getDocument(virtualFile);

    Collection<Bookmark> bookmarks = document == null ? Collections.emptyList() : getFileBookmarks(virtualFile);
    for (Bookmark bookmark : bookmarks) {
      int line = bookmark.getLine();
      if (line == -1) continue;
      if (nameRange.intersects(document.getLineStartOffset(line), document.getLineEndOffset(line))) {
        return bookmark;
      }
    }
    return null;
  }

  @Override
  public Element getState() {
    Element container = new Element("BookmarkManager");
    writeExternal(container);
    return container;
  }

  @Override
  public void loadState(@NotNull Element state) {
    myPendingState.set(readExternal(state));

    StartupManager.getInstance(myProject).runAfterOpened(() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        List<Bookmark> newList = myPendingState.getAndSet(null);
        if (newList != null) {
          applyNewState(newList, true);
        }
      }, ModalityState.nonModal(), myProject.getDisposed());
    });
  }

  @Override
  public void noStateLoaded() {
    LOG.info("no state loaded for old bookmarks");
  }

  @TestOnly
  public void applyNewStateInTestMode(@NotNull List<Bookmark> newList) {
    applyNewState(newList, false);
  }

  private void applyNewState(@NotNull List<Bookmark> newList, boolean fireEvents) {
    if (!myBookmarks.isEmpty()) {
      Bookmark[] bookmarks = myBookmarks.values().toArray(new Bookmark[0]);
      for (Bookmark bookmark : bookmarks) {
        bookmark.release();
      }
      myBookmarks.clear();
    }

    int bookmarkIndex = newList.size() - 1;
    List<Bookmark> addedBookmarks = new ArrayList<>(newList.size());
    for (Bookmark bookmark : newList) {
      OpenFileDescriptor target = bookmark.init(myProject);
      if (target == null) {
        continue;
      }

      if (target.getLine() == -1 && findFileBookmark(target.getFile()) != null) {
        continue;
      }

      bookmark.index = bookmarkIndex--;

      char mnemonic = bookmark.getMnemonic();
      if (mnemonic != Character.MIN_VALUE ) {
        Bookmark old = findBookmarkForMnemonic(mnemonic);
        if (old != null) {
          removeBookmark(old);
        }
      }

      myBookmarks.putValue(target.getFile(), bookmark);
      addedBookmarks.add(bookmark);
    }

    if (fireEvents) {
      for (Bookmark bookmark : addedBookmarks) {
        getPublisher().bookmarkAdded(bookmark);
      }
    }
  }

  private static @NotNull List<Bookmark> readExternal(@NotNull Element element) {
    List<Bookmark> result = new ArrayList<>();
    for (Element bookmarkElement : element.getChildren("bookmark")) {
      String url = bookmarkElement.getAttributeValue("url");
      if (StringUtil.isEmptyOrSpaces(url)) {
        continue;
      }

      String line = bookmarkElement.getAttributeValue("line");
      String description = StringUtil.notNullize(bookmarkElement.getAttributeValue("description"));
      String mnemonic = bookmarkElement.getAttributeValue("mnemonic");

      int lineIndex = -1;
      if (line != null) {
        try {
          lineIndex = Integer.parseInt(line);
        }
        catch (NumberFormatException ignore) {
          // Ignore. Will miss bookmark if line number cannot be parsed
          continue;
        }
      }
      Bookmark bookmark = new Bookmark(url, lineIndex, description);
      if (mnemonic != null && mnemonic.length() == 1) {
        bookmark.setMnemonic(mnemonic.charAt(0));
      }
      result.add(bookmark);
    }
    return result;
  }

  private void writeExternal(Element element) {
    List<Bookmark> bookmarks = new ArrayList<>(myBookmarks.values());
    // store in reverse order so that loadExternal() will assign them correct indices
    bookmarks.sort(Comparator.<Bookmark>comparingInt(o -> o.index).reversed());

    for (Bookmark bookmark : bookmarks) {
      if (!bookmark.isValid()) continue;
      Element bookmarkElement = new Element("bookmark");

      bookmarkElement.setAttribute("url", bookmark.getFile().getUrl());

      String description = bookmark.nullizeEmptyDescription();
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
   */
  public void moveBookmarkUp(@NotNull Bookmark bookmark) {
    ThreadingAssertions.assertEventDispatchThread();
    final int index = bookmark.index;
    if (index > 0) {
      Bookmark other = ContainerUtil.find(myBookmarks.values(), b -> b.index == index - 1);
      other.index = index;
      bookmark.index = index - 1;
      EventQueue.invokeLater(() -> {
        getPublisher().bookmarkChanged(bookmark);
        getPublisher().bookmarkChanged(other);
      });
    }
  }

  /**
   * Try to move bookmark one position down in the list
   */
  public void moveBookmarkDown(@NotNull Bookmark bookmark) {
    ThreadingAssertions.assertEventDispatchThread();
    final int index = bookmark.index;
    if (index < myBookmarks.values().size() - 1) {
      Bookmark other = ContainerUtil.find(myBookmarks.values(), b -> b.index == index + 1);
      other.index = index;
      bookmark.index = index + 1;
      EventQueue.invokeLater(() -> {
        getPublisher().bookmarkChanged(bookmark);
        getPublisher().bookmarkChanged(other);
      });
    }
  }

  public @Nullable Bookmark findLineBookmark(@NotNull Editor editor, boolean isWrapped, boolean next) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (file == null) return null;
    List<Bookmark> bookmarksForDocument = new ArrayList<>(myBookmarks.get(file));
    if (bookmarksForDocument.isEmpty()) return null;
    int sign = next ? 1 : -1;
    bookmarksForDocument.sort((o1, o2) -> sign * (o1.getLine() - o2.getLine()));
    int caretLine = editor.getCaretModel().getLogicalPosition().line;
    for (Bookmark bookmark : bookmarksForDocument) {
      if (next && bookmark.getLine() > caretLine) return bookmark;
      if (!next && bookmark.getLine() < caretLine) return bookmark;
    }
    return isWrapped && !bookmarksForDocument.isEmpty() ? bookmarksForDocument.get(0) : null;
  }

  public void deleteMnemonic(@NotNull Bookmark bookmark) {
    if (BookmarkType.DEFAULT != bookmark.getType()) updateMnemonic(bookmark, BookmarkType.DEFAULT.getMnemonic());
  }

  public void setMnemonic(@NotNull Bookmark bookmark, char c) {
    ThreadingAssertions.assertEventDispatchThread();
    final Bookmark old = findBookmarkForMnemonic(c);
    if (old != null) removeBookmark(old);
    updateMnemonic(bookmark, c);
  }

  private void updateMnemonic(@NotNull Bookmark bookmark, char c) {
    bookmark.setMnemonic(c);
    getPublisher().bookmarkChanged(bookmark);
    bookmark.updateHighlighter();
  }

  public void setDescription(@NotNull Bookmark bookmark, @NotNull @NlsSafe String description) {
    ThreadingAssertions.assertEventDispatchThread();
    bookmark.setDescription(description);
    getPublisher().bookmarkChanged(bookmark);
  }

  private void colorsChanged() {
    for (Bookmark bookmark : myBookmarks.values()) {
      bookmark.updateHighlighter();
    }
  }

  private final class MyDocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent e) {
      Document doc = e.getDocument();
      VirtualFile file = FileDocumentManager.getInstance().getFile(doc);
      if (file != null) {
        for (Bookmark bookmark : myBookmarks.get(file)) {
          if (bookmark.getLine() == -1) continue;
          List<BookmarkInfo> list = myBeforeChangeData.computeIfAbsent(doc, __ -> new ArrayList<>());
          list.add(new BookmarkInfo(bookmark,
                                    bookmark.getLine(),
                                    doc.getText(new TextRange(doc.getLineStartOffset(bookmark.getLine()),
                                                              doc.getLineEndOffset(bookmark.getLine())))));
        }
      }
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent e) {
      if (!ApplicationManager.getApplication().isDispatchThread()) {
        return;// Changes in lightweight documents are irrelevant to bookmarks and have to be ignored
      }
      VirtualFile file = FileDocumentManager.getInstance().getFile(e.getDocument());
      List<Bookmark> bookmarksToRemove = null;
      if (file != null) {
        for (Bookmark bookmark : myBookmarks.get(file)) {
          if (!bookmark.isValid() || isDuplicate(bookmark, file, bookmarksToRemove)) {
            if (bookmarksToRemove == null) {
              bookmarksToRemove = new ArrayList<>();
            }
            bookmarksToRemove.add(bookmark);
          }
        }
      }

      if (bookmarksToRemove != null) {
        for (Bookmark bookmark : bookmarksToRemove) {
          moveToDeleted(bookmark);
        }
      }

      myBeforeChangeData.remove(e.getDocument());

      for (Iterator<Map.Entry<DeletedDocumentBookmarkKey, Bookmark>> iterator = myDeletedDocumentBookmarks.entrySet().iterator();
           iterator.hasNext(); ) {
        Map.Entry<DeletedDocumentBookmarkKey, Bookmark> entry = iterator.next();

        VirtualFile virtualFile = entry.getKey().file;
        if (!virtualFile.isValid()) {
          iterator.remove();
          continue;
        }

        Bookmark bookmark = entry.getValue();
        Document document = bookmark.getCachedDocument();
        if (document == null || !bookmark.getFile().equals(virtualFile)) {
          continue;
        }
        int line = entry.getKey().line;
        if (document.getLineCount() <= line) {
          continue;
        }

        String lineContent = getLineContent(document, line);

        String bookmarkedText = entry.getKey().text;
        //'move statement up' action kills line bookmark: fix for single line movement up/down
        if (!bookmarkedText.equals(lineContent)
            && line > 1
            && (bookmarkedText.equals(StringUtil.trimEnd(e.getNewFragment().toString(), "\n"))
                ||
                bookmarkedText.equals(StringUtil.trimEnd(e.getOldFragment().toString(), "\n")))) {
          line -= 2;
          lineContent = getLineContent(document, line);
        }
        if (bookmarkedText.equals(lineContent) && findEditorBookmark(document, line) == null) {
          Bookmark restored = addTextBookmark(bookmark.getFile(), line, bookmark.getDescription());
          if (bookmark.getMnemonic() != 0) {
            setMnemonic(restored, bookmark.getMnemonic());
          }
          iterator.remove();
        }
      }
    }

    private boolean isDuplicate(Bookmark bookmark, @NotNull VirtualFile file, @Nullable List<Bookmark> toRemove) {
      // it's quadratic but oh well. let's hope users are sane enough not to have thousands of bookmarks in one file.
      for (Bookmark b : myBookmarks.get(file)) {
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

    private void moveToDeleted(Bookmark bookmark) {
      List<BookmarkInfo> list = myBeforeChangeData.get(bookmark.getCachedDocument());

      if (list != null) {
        for (BookmarkInfo bookmarkInfo : list) {
          if (bookmarkInfo.bookmark == bookmark) {
            removeBookmark(bookmark);
            myDeletedDocumentBookmarks.put(new DeletedDocumentBookmarkKey(bookmark.getFile(), bookmarkInfo.line, bookmarkInfo.text), bookmark);
            break;
          }
        }
      }
    }

    private static String getLineContent(Document document, int line) {
      int start = document.getLineStartOffset(line);
      int end = document.getLineEndOffset(line);
      return document.getText(new TextRange(start, end));
    }
  }
}

