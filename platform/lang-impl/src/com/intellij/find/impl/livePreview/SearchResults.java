package com.intellij.find.impl.livePreview;


import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;

public class SearchResults {

  private int myActualFound = 0;

  private List<SearchResultsListener> myListeners = new ArrayList<SearchResultsListener>();

  private LiveOccurrence myCursor;

  private List<LiveOccurrence> myOccurrences = new ArrayList<LiveOccurrence>();
  private Set<LiveOccurrence> myExcluded = new HashSet<LiveOccurrence>();

  private Editor myEditor;

  private FindModel myFindModel;

  private int myMatchesLimit = 100;

  public SearchResults(Editor editor) {
    myEditor = editor;
  }

  public int getActualFound() {
    return myActualFound;
  }

  public boolean hasMatches() {
    return !getOccurrences().isEmpty();
  }

  public FindModel getFindModel() {
    return myFindModel;
  }

  public boolean isExcluded(LiveOccurrence occurrence) {
    return myExcluded.contains(occurrence);
  }

  public void exclude(LiveOccurrence occurrence) {
    if (myExcluded.contains(occurrence)) {
      myExcluded.remove(occurrence);
    } else {
      myExcluded.add(occurrence);
    }
    notifyChanged();
  }

  public Set<LiveOccurrence> getExcluded() {
    return myExcluded;
  }

  public interface SearchResultsListener {
    void searchResultsUpdated(SearchResults sr);
    void editorChanged(SearchResults sr, Editor oldEditor);

    void cursorMoved();
  }

  public void addListener(SearchResultsListener srl) {
    myListeners.add(srl);
  }

  public void removeListener(SearchResultsListener srl) {
    myListeners.remove(srl);
  }

  public int getMatchesLimit() {
    return myMatchesLimit;
  }

  public void setMatchesLimit(int matchesLimit) {
    myMatchesLimit = matchesLimit;
  }

  public LiveOccurrence getCursor() {
    return myCursor;
  }

  public List<LiveOccurrence> getOccurrences() {
    return myOccurrences;
  }

  public Project getProject() {
    return myEditor.getProject();
  }

  public synchronized void setEditor(Editor editor) {
    Editor oldOne = myEditor;
    myEditor = editor;
    notifyEditorChanged(oldOne);
  }

  private void notifyEditorChanged(Editor oldOne) {
    for (SearchResultsListener listener : myListeners) {
      listener.editorChanged(this, oldOne);
    }
  }

  public synchronized Editor getEditor() {
    return myEditor;
  }

  private static void findResultsToOccurrences(ArrayList<FindResult> results, Collection<LiveOccurrence> occurrences) {
    for (FindResult r : results) {
      LiveOccurrence occurrence = new LiveOccurrence();
      occurrence.setPrimaryRange(r);
      occurrences.add(occurrence);
    }
  }

  public void updateThreadSafe(final FindModel findModel) {
    final ArrayList<LiveOccurrence> occurrences = new ArrayList<LiveOccurrence>();
    final Editor editor = getEditor();

    final ArrayList<FindResult> results = new ArrayList<FindResult>();
    if (findModel != null) {

      TextRange r = findModel.isGlobal() ? new TextRange(0, Integer.MAX_VALUE) :
                    new TextRange(editor.getSelectionModel().getSelectionStart(),
                                  editor.getSelectionModel().getSelectionEnd());
      if (r.getLength() == 0) {
        r = new TextRange(0, Integer.MAX_VALUE);
      }
      int offset = r.getStartOffset();
      VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());

      while (true) {
        FindManager findManager = FindManager.getInstance(editor.getProject());
        FindResult result = findManager.findString(editor.getDocument().getCharsSequence(), offset, findModel, virtualFile);
        if (!result.isStringFound()) break;
        int newOffset = result.getEndOffset();
        if (offset == newOffset || result.getEndOffset() > r.getEndOffset()) break;
        offset = newOffset;
        results.add(result);

        if (results.size() > myMatchesLimit) break;
      }
      if (results.size() < myMatchesLimit) {

        findResultsToOccurrences(results, occurrences);
      }
    }

    final Runnable r = new Runnable() {
      @Override
      public void run() {
        searchCompleted(occurrences, results.size(), editor, findModel);
      }
    };

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      ApplicationManager.getApplication().invokeLater(r);
    } else {
      r.run();
    }
  }

  private void searchCompleted(List<LiveOccurrence> occurrences, int size, Editor editor, FindModel findModel) {
    if (editor == getEditor()) {
      myOccurrences = occurrences;
      final TextRange oldCursorRange = myCursor != null ? myCursor.getPrimaryRange() : null;
      Collections.sort(myOccurrences, new Comparator<LiveOccurrence>() {
        @Override
        public int compare(LiveOccurrence liveOccurrence, LiveOccurrence liveOccurrence1) {
          return liveOccurrence.getPrimaryRange().getStartOffset() - liveOccurrence1.getPrimaryRange().getStartOffset();
        }
      });

      updateCursor(oldCursorRange);
      myFindModel = findModel;
      myActualFound = size;
      notifyChanged();
      if (oldCursorRange == null) {
        notifyCursorMoved();
      }
    }
  }

  private void updateCursor(TextRange oldCursorRange) {
    if (!tryToRepairOldCursor(oldCursorRange)) {
      LiveOccurrence afterCaret = firstOccurrenceAfterCaret();
      if (afterCaret != null) {
        myCursor = afterCaret;
      } else {
        LiveOccurrence occurrence = firstVisibleOccurrence();
        myCursor = occurrence;
      }
    }
  }

  private void notifyChanged() {
    for (SearchResultsListener listener : myListeners) {
      listener.searchResultsUpdated(this);
    }
  }

  static boolean insideVisibleArea(Editor e, TextRange r) {
    Rectangle visibleArea = e.getScrollingModel().getVisibleArea();
    Point point = e.logicalPositionToXY(e.offsetToLogicalPosition(r.getStartOffset()));

    return visibleArea.contains(point);
  }

  @Nullable
  private LiveOccurrence firstVisibleOccurrence() {
    int offset = Integer.MAX_VALUE;
    LiveOccurrence firstOccurrence = null;
    LiveOccurrence firstVisibleOccurrence = null;
    for (LiveOccurrence o : getOccurrences()) {
      if (insideVisibleArea(myEditor, o.getPrimaryRange())) {
        if (firstVisibleOccurrence == null || o.getPrimaryRange().getStartOffset() < firstVisibleOccurrence.getPrimaryRange().getStartOffset()) {
          firstVisibleOccurrence = o;
        }
      }
      if (o.getPrimaryRange().getStartOffset() < offset) {
        offset = o.getPrimaryRange().getStartOffset();
        firstOccurrence = o;
      }
    }
    return firstVisibleOccurrence != null ? firstVisibleOccurrence : firstOccurrence;
  }

  @Nullable
  private LiveOccurrence firstOccurrenceAfterCaret() {
    LiveOccurrence afterCaret = null;
    int caret = myEditor.getCaretModel().getOffset();
    for (LiveOccurrence occurrence : getOccurrences()) {
      if (occurrence.getPrimaryRange().getStartOffset() >= caret) {
        if (afterCaret == null || occurrence.getPrimaryRange().getStartOffset() < afterCaret.getPrimaryRange().getStartOffset() ) {
          afterCaret = occurrence;
        }
      }
    }
    return afterCaret;
  }

  private boolean tryToRepairOldCursor(TextRange oldCursorRange) {
    if (oldCursorRange == null) return false;
    LiveOccurrence mayBeOldCursor = null;
    for (LiveOccurrence searchResult : getOccurrences()) {
      if (searchResult.getPrimaryRange().intersects(oldCursorRange)) {
        mayBeOldCursor = searchResult;
        break;
      }
    }
    if (mayBeOldCursor != null) {
      myCursor = mayBeOldCursor;
      return true;
    }
    return false;
  }

  @Nullable
  private LiveOccurrence prevOccurrence(LiveOccurrence o) {
    for (int i = 0; i < getOccurrences().size(); ++i) {
      if (o == getOccurrences().get(i))  {
        if (i > 0) {
          return getOccurrences().get(i - 1);
        }
      }
    }
    return null;
  }

  @Nullable
  private LiveOccurrence nextOccurrence(LiveOccurrence o) {
    boolean found = false;
    for (LiveOccurrence occurrence : getOccurrences()) {
      if (found) {
        return occurrence;
      }
      if (o == occurrence) {
        found = true;
      }
    }
    return null;
  }

  public void prevOccurrence() {
    LiveOccurrence prev = prevOccurrence(myCursor);
    if (prev == null && !getOccurrences().isEmpty()) {
      prev = getOccurrences().get(getOccurrences().size() - 1);
    }
    moveCursorTo(prev);
  }

  public void nextOccurrence() {
    LiveOccurrence next = nextOccurrence(myCursor);
    if (next == null && !getOccurrences().isEmpty()) {
      next = getOccurrences().get(0);
    }
    moveCursorTo(next);
  }

  public void moveCursorTo(LiveOccurrence next) {
    if (next != null) {
      setCursor(next);
    }
  }

  private void setCursor(LiveOccurrence liveOccurrence) {
    myCursor = liveOccurrence;
    notifyCursorMoved();
  }

  private void notifyCursorMoved() {
    for (SearchResultsListener listener : myListeners) {
      listener.cursorMoved();
    }
  }
}
