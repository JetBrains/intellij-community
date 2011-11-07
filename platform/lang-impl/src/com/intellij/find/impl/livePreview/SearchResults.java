package com.intellij.find.impl.livePreview;


import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.FindUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.PatternSyntaxException;

public class SearchResults implements DocumentListener {

  public int getStamp() {
    return ++myStamp;
  }

  @Override
  public void beforeDocumentChange(DocumentEvent event) {
    myCursorPositions.clear();
  }

  @Override
  public void documentChanged(DocumentEvent event) {
  }

  public enum Direction {UP, DOWN}

  private int myActualFound = 0;

  private List<SearchResultsListener> myListeners = new ArrayList<SearchResultsListener>();

  private @Nullable LiveOccurrence myCursor;

  private List<LiveOccurrence> myOccurrences = new ArrayList<LiveOccurrence>();

  private Set<RangeMarker> myExcluded = new HashSet<RangeMarker>();

  private Editor myEditor;
  private FindModel myFindModel;

  private int myMatchesLimit = 100;

  private boolean myNotFoundState = false;

  private boolean myDisposed = false;

  private int myStamp = 0;

  private int myLastUpdatedStamp = -1;

  private Stack<Pair<FindModel, LiveOccurrence>> myCursorPositions = new Stack<Pair<FindModel, LiveOccurrence>>();

  public SearchResults(Editor editor) {
    myEditor = editor;
    myEditor.getDocument().addDocumentListener(this);
  }

  public void setNotFoundState(boolean isForward) {
    myNotFoundState = true;
    FindModel findModel = new FindModel();
    findModel.copyFrom(myFindModel);
    findModel.setForward(isForward);
    FindUtil.processNotFound(myEditor, findModel.getStringToFind(), findModel, getProject());
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
    for (RangeMarker rangeMarker : myExcluded) {
      if (rangeMarker.getStartOffset() == occurrence.getPrimaryRange().getStartOffset() && rangeMarker.getEndOffset() == occurrence.getPrimaryRange().getEndOffset()) {
        return true;
      }
    }
    return false;
  }

  public void exclude(LiveOccurrence occurrence) {
    boolean include = false;
    final TextRange r = occurrence.getPrimaryRange();
    for (RangeMarker rangeMarker : myExcluded) {
      if (rangeMarker.getStartOffset() == r.getStartOffset() && rangeMarker.getEndOffset() == r.getEndOffset()) {
        myExcluded.remove(rangeMarker);
        rangeMarker.dispose();
        include = true;
        break;
      }
    }
    if (!include) {
      myExcluded.add(myEditor.getDocument().createRangeMarker(r.getStartOffset(), r.getEndOffset(), true));
    }
    notifyChanged();
  }

  public Set<RangeMarker> getExcluded() {
    return myExcluded;
  }

  public interface SearchResultsListener {

    void searchResultsUpdated(SearchResults sr);
    void editorChanged(SearchResults sr, Editor oldEditor);
    void cursorMoved(boolean toChangeSelection);

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

  @Nullable
  public LiveOccurrence getCursor() {
    return myCursor;
  }

  public List<LiveOccurrence> getOccurrences() {
    return myOccurrences;
  }

  @Nullable
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

  public void clear() {
    searchCompleted(new ArrayList<LiveOccurrence>(), 0, getEditor(), null, false, null, getStamp());
  }
  
  private static class BombedCharSequence implements CharSequence {
    private CharSequence delegate;
    private long myTime;
    private long i = 0;

    public BombedCharSequence(CharSequence sequence, long time) {
      delegate = sequence;
      myTime = time;
    }

    @Override
    public int length() {
      check();
      return delegate.length();
    }

    @Override
    public char charAt(int i) {
      check();
      return delegate.charAt(i);
    }

    private void check() {
      ++i;
      if (i % 1000 == 0) {
        long l = System.currentTimeMillis();
        if (l >= myTime) {
          throw new ProcessCanceledException();
        }
      }
    }

    @Override
    public CharSequence subSequence(int i, int i1) {
      check();
      return delegate.subSequence(i, i1);
    }
  }

  public void updateThreadSafe(final FindModel findModel, final boolean toChangeSelection, @Nullable final TextRange next, final int stamp) {
    if (myDisposed) return;
    final ArrayList<LiveOccurrence> occurrences = new ArrayList<LiveOccurrence>();
    final Editor editor = getEditor();

    final ArrayList<FindResult> results = new ArrayList<FindResult>();
    if (findModel != null) {
      final FutureResult<int[]> startsRef = new FutureResult<int[]>();
      final FutureResult<int[]> endsRef = new FutureResult<int[]>();
      getSelection(editor, startsRef, endsRef);

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          int[] starts = new int[0];
          int[] ends = new int[0];
          try {
            starts = startsRef.get();
            ends = endsRef.get();
          }
          catch (InterruptedException ignore) {
          }
          catch (ExecutionException ignore) {
          }

          if (starts.length == 0 || findModel.isGlobal()) {
            findInRange(new TextRange(0, Integer.MAX_VALUE), editor, findModel, results);
          } else {
            for (int i = 0; i < starts.length; ++i) {
              findInRange(new TextRange(starts[i], ends[i]), editor, findModel, results);
            }
          }

          if (results.size() < myMatchesLimit) {
            findResultsToOccurrences(results, occurrences);
          }

          final Runnable searchCompletedRunnable = new Runnable() {
            @Override
            public void run() {
              searchCompleted(occurrences, results.size(), editor, findModel, toChangeSelection, next, stamp);
            }
          };

          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            SwingUtilities.invokeLater(searchCompletedRunnable);
          } else {
            searchCompletedRunnable.run();
          }
        }
      });
    }
  }

  private static void getSelection(final Editor editor, final FutureResult<int[]> starts, final FutureResult<int[]> ends) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      SelectionModel selection = editor.getSelectionModel();
      starts.set(selection.getBlockSelectionStarts());
      ends.set(selection.getBlockSelectionEnds());
    } else {
      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          @Override
          public void run() {
            SelectionModel selection = editor.getSelectionModel();
            starts.set(selection.getBlockSelectionStarts());
            ends.set(selection.getBlockSelectionEnds());
          }
        });
      }
      catch (InterruptedException ignore) {
      }
      catch (InvocationTargetException ignore) {
      }
    }
  }

  private void findInRange(TextRange r, Editor editor, FindModel findModel, ArrayList<FindResult> results) {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());

    int offset = r.getStartOffset();

    while (true) {
      FindManager findManager = FindManager.getInstance(editor.getProject());
      FindResult result;
      try {
        BombedCharSequence
          bombedCharSequence = new BombedCharSequence(editor.getDocument().getCharsSequence(), System.currentTimeMillis() + 3000);
        result = findManager.findString(bombedCharSequence, offset, findModel, virtualFile);
      } catch(PatternSyntaxException e) {
        result = null;
      } catch (ProcessCanceledException e) {
        result = null;
      }
      if (result == null || !result.isStringFound()) break;
      int newOffset = result.getEndOffset();
      if (offset == newOffset || result.getEndOffset() > r.getEndOffset()) break;
      offset = newOffset;
      results.add(result);

      if (results.size() > myMatchesLimit) break;
    }
  }

  public void dispose() {
    myDisposed = true;
    myEditor.getDocument().removeDocumentListener(this);
  }

  private void searchCompleted(List<LiveOccurrence> occurrences, int size, Editor editor, @Nullable FindModel findModel,
                               boolean toChangeSelection, @Nullable TextRange next, int stamp) {
    if (stamp < myLastUpdatedStamp){
      return;
    }
    myLastUpdatedStamp = stamp;
    if (editor == getEditor() && !myDisposed) {
      myOccurrences = occurrences;
      final TextRange oldCursorRange = myCursor != null ? myCursor.getPrimaryRange() : null;
      Collections.sort(myOccurrences, new Comparator<LiveOccurrence>() {
        @Override
        public int compare(LiveOccurrence liveOccurrence, LiveOccurrence liveOccurrence1) {
          return liveOccurrence.getPrimaryRange().getStartOffset() - liveOccurrence1.getPrimaryRange().getStartOffset();
        }
      });

      myFindModel = findModel;
      updateCursor(oldCursorRange, next);
      updateExcluded();
      myActualFound = size;
      notifyChanged();
      if (oldCursorRange == null || myCursor == null || !myCursor.getPrimaryRange().equals(oldCursorRange)) {
        notifyCursorMoved(toChangeSelection);
      }
    }
  }

  private void updateExcluded() {
    Set<RangeMarker> invalid = new HashSet<RangeMarker>();
    for (RangeMarker marker : myExcluded) {
      if (!marker.isValid()) {
        invalid.add(marker);
        marker.dispose();
      }
    }
    myExcluded.removeAll(invalid);
  }

  private void updateCursor(@Nullable TextRange oldCursorRange, @Nullable TextRange next) {
    boolean justReplaced = next != null;
    boolean toPush = true;
    if (justReplaced || (toPush = !repairCursorFromStack())) {
      if (justReplaced || !tryToRepairOldCursor(oldCursorRange)) {
        if (myFindModel != null) {
          if(oldCursorRange != null && !myFindModel.isGlobal()) {
            myCursor = firstOccurrenceAfterOffset(oldCursorRange.getEndOffset());
          } else {
            if (justReplaced) {
              nextOccurrence(false, next, false, justReplaced);
            } else {
              LiveOccurrence afterCaret = oldCursorRange == null ? firstOccurrenceAtOrAfterCaret() : firstOccurrenceAfterCaret();
              if (afterCaret != null) {
                myCursor = afterCaret;
              } else {
                myCursor = null;
              }
            }
          }
        } else {
          myCursor = null;
        }
      }
    }
    if (!justReplaced && myCursor == null && hasMatches()) {
      nextOccurrence(true, oldCursorRange, false, false);
    }
    if (toPush && myCursor != null){
      push();
    }
  }

  private boolean repairCursorFromStack() {
    if (myCursorPositions.size() >= 2) {
      final Pair<FindModel, LiveOccurrence> oldPosition = myCursorPositions.get(myCursorPositions.size() - 2);
      if (oldPosition.first.equals(myFindModel)) {
        LiveOccurrence newCursor;
        if ((newCursor = findOccurrenceEqualTo(oldPosition.second)) != null) {
          myCursorPositions.pop();
          myCursor = newCursor;
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private LiveOccurrence findOccurrenceEqualTo(LiveOccurrence occurrence) {
    for (LiveOccurrence liveOccurrence : myOccurrences) {
      if (liveOccurrence.getPrimaryRange().equals(occurrence.getPrimaryRange())) {
        return liveOccurrence;
      }
    }
    return null;
  }

  @Nullable
  private LiveOccurrence firstOccurrenceAtOrAfterCaret() {
    int offset = getEditor().getCaretModel().getOffset();
    for (LiveOccurrence occurrence : myOccurrences) {
      if (offset <= occurrence.getPrimaryRange().getEndOffset() && offset >= occurrence.getPrimaryRange().getStartOffset()) {
        return occurrence;
      }
    }
    return firstOccurrenceAfterCaret();
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
  private LiveOccurrence firstOccurrenceBeforeCaret() {
    int offset = getEditor().getCaretModel().getOffset();
    return firstOccurrenceBeforeOffset(offset);
  }

  @Nullable
  private LiveOccurrence firstOccurrenceBeforeOffset(int offset) {
    for (int i = getOccurrences().size()-1; i >= 0; --i) {
      if (getOccurrences().get(i).getPrimaryRange().getEndOffset() < offset) {
        return getOccurrences().get(i);
      }
    }
    return null;
  }

  @Nullable
  private LiveOccurrence firstOccurrenceAfterCaret() {
    int caret = myEditor.getCaretModel().getOffset();
    return firstOccurrenceAfterOffset(caret);
  }

  @Nullable
  private LiveOccurrence firstOccurrenceAfterOffset(int offset) {
    LiveOccurrence afterCaret = null;
    for (LiveOccurrence occurrence : getOccurrences()) {
      if (occurrence.getPrimaryRange().getStartOffset() >= offset) {
        if (afterCaret == null || occurrence.getPrimaryRange().getStartOffset() < afterCaret.getPrimaryRange().getStartOffset() ) {
          afterCaret = occurrence;
        }
      }
    }
    return afterCaret;
  }

  private boolean tryToRepairOldCursor(@Nullable TextRange oldCursorRange) {
    if (oldCursorRange == null) return false;
    LiveOccurrence mayBeOldCursor = null;
    for (LiveOccurrence searchResult : getOccurrences()) {
      if (searchResult.getPrimaryRange().intersects(oldCursorRange)) {
        mayBeOldCursor = searchResult;
      }
      if (searchResult.getPrimaryRange().getStartOffset() == oldCursorRange.getStartOffset()) {
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
  private LiveOccurrence prevOccurrence(TextRange range) {
    for (int i = getOccurrences().size() - 1; i >= 0; --i) {
      final LiveOccurrence occurrence = getOccurrences().get(i);
      if (occurrence.getPrimaryRange().getEndOffset() <= range.getStartOffset())  {
          return occurrence;
      }
    }
    return null;
  }

  @Nullable
  private LiveOccurrence nextOccurrence(TextRange range) {
    for (LiveOccurrence occurrence : getOccurrences()) {
      if (occurrence.getPrimaryRange().getStartOffset() >= range.getEndOffset()) {
        return occurrence;
      }
    }
    return null;
  }

  public void prevOccurrence() {
    LiveOccurrence next = null;
    if (myFindModel == null) return;
    boolean processFromTheBeginning = false;
    if (myNotFoundState) {
      myNotFoundState = false;
      processFromTheBeginning = true;
    }
    if (!myFindModel.isGlobal()) {
      if (myCursor != null) {
        next = prevOccurrence(myCursor.getPrimaryRange());
      }
    } else {
      next = firstOccurrenceBeforeCaret();
    }
    if (next == null) {
      if (processFromTheBeginning) {
        if (hasMatches()) {
          next = getOccurrences().get(getOccurrences().size()-1);
        }
      } else {
        setNotFoundState(false);
      }
    }

    moveCursorTo(next);
    push();
  }

  private void push() {
    myCursorPositions.push(new Pair<FindModel, LiveOccurrence>(myFindModel, myCursor));
  }

  public void nextOccurrence() {
    if (myFindModel == null) return;
    nextOccurrence(false, myCursor != null ? myCursor.getPrimaryRange() : null, true, false);
    push();
  }

  private void nextOccurrence(boolean processFromTheBeginning, TextRange cursor, boolean toNotify, boolean justReplaced) {
    LiveOccurrence next;
    if (myNotFoundState) {
      myNotFoundState = false;
      processFromTheBeginning = true;
    }
    if ((!myFindModel.isGlobal() || justReplaced) && cursor != null) {
      next = nextOccurrence(cursor);
    } else {
      next = firstOccurrenceAfterCaret();
    }
    if (next == null) {
      if (processFromTheBeginning) {
        if (hasMatches()) {
          next = getOccurrences().get(0);
        }
      } else {
        setNotFoundState(true);
      }
    }
    if (toNotify) {
      moveCursorTo(next);
    } else {
      myCursor = next;
    }
  }

  public void moveCursorTo(LiveOccurrence next) {
    if (next != null) {
      myCursor = next;
      notifyCursorMoved(true);
    }
  }

  private void notifyCursorMoved(boolean toChangeSelection) {
    for (SearchResultsListener listener : myListeners) {
      listener.cursorMoved(toChangeSelection);
    }
  }
}
