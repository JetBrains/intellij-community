// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl.livePreview;


import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.FindUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.PatternSyntaxException;

public class SearchResults implements DocumentListener, CaretListener {

  public int getStamp() {
    return ++myStamp;
  }

  @Override
  public void beforeDocumentChange(@NotNull DocumentEvent event) {
    myCursorPositions.clear();
  }

  @Override
  public void caretPositionChanged(@NotNull CaretEvent event) {
    Caret caret = event.getCaret();
    if (caret != null && myEditor.getCaretModel().getCaretCount() == 1) {
      int offset = caret.getOffset();
      FindResult occurrenceAtCaret = getOccurrenceAtCaret();
      if (occurrenceAtCaret != null && occurrenceAtCaret != myCursor) {
        moveCursorTo(occurrenceAtCaret, false, false);
        myEditor.getCaretModel().moveToOffset(offset);
        if (myFindModel.isGlobal()) {
          myEditor.getSelectionModel().removeSelection();
        }
        notifyCursorMoved();
      }
    }
  }

  public enum Direction {UP, DOWN}

  private final List<SearchResultsListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private @Nullable FindResult myCursor;

  private @NotNull List<FindResult> myOccurrences = new ArrayList<>();

  private final Set<RangeMarker> myExcluded = new HashSet<>();

  private final @NotNull Editor myEditor;
  private final @NotNull Project myProject;
  private FindModel myFindModel;

  private int myMatchesLimit = 100;

  private boolean myNotFoundState;

  private boolean myDisposed;

  private int myStamp;

  private int myLastUpdatedStamp = -1;
  private long myDocumentTimestamp;
  private boolean myUpdating;
  private SearchResults.Direction myPendingSearch;

  private final Stack<Pair<FindModel, FindResult>> myCursorPositions = new Stack<>();

  private final SelectionManager mySelectionManager;
  private final SearchArea globalSearchArea = SearchArea.create(new int[]{0}, new int[]{Integer.MAX_VALUE});

  public SearchResults(@NotNull Editor editor, @NotNull Project project) {
    myEditor = editor;
    myProject = project;
    myEditor.getDocument().addDocumentListener(this);
    myEditor.getCaretModel().addCaretListener(this);
    mySelectionManager = new SelectionManager(this); // important to initialize last for accessing other fields
  }

  private void setNotFoundState(boolean isForward) {
    myNotFoundState = true;
    FindModel findModel = new FindModel();
    findModel.copyFrom(myFindModel);
    findModel.setForward(isForward);
    int caretOffset = myCursor != null ? myCursor.getEndOffset() : myEditor.getCaretModel().getOffset();
    FindUtil.processNotFound(myEditor, caretOffset, findModel.getStringToFind(), findModel, getProject());
  }

  public int getMatchesCount() {
    return myOccurrences.size();
  }

  public boolean hasMatches() {
    return !getOccurrences().isEmpty();
  }

  public FindModel getFindModel() {
    return myFindModel;
  }

  public boolean isExcluded(FindResult occurrence) {
    for (RangeMarker rangeMarker : myExcluded) {
      if (TextRange.areSegmentsEqual(rangeMarker, occurrence)) {
        return true;
      }
    }
    return false;
  }

  public void exclude(FindResult occurrence) {
    boolean include = false;
    for (RangeMarker rangeMarker : myExcluded) {
      if (TextRange.areSegmentsEqual(rangeMarker, occurrence)) {
        myExcluded.remove(rangeMarker);
        rangeMarker.dispose();
        include = true;
        break;
      }
    }
    if (!include) {
      myExcluded.add(myEditor.getDocument().createRangeMarker(occurrence.getStartOffset(), occurrence.getEndOffset(), true));
    }
    notifyChanged();
  }

  public Set<RangeMarker> getExcluded() {
    return myExcluded;
  }

  public interface SearchResultsListener {

    void searchResultsUpdated(@NotNull SearchResults sr);
    void cursorMoved();

    default void updateFinished() {}
    default void beforeSelectionUpdate() {}
    default void afterSelectionUpdate() {}
  }
  public void addListener(@NotNull SearchResultsListener srl) {
    myListeners.add(srl);
  }

  public void removeListener(@NotNull SearchResultsListener srl) {
    myListeners.remove(srl);
  }

  public int getMatchesLimit() {
    return myMatchesLimit;
  }

  public void setMatchesLimit(int matchesLimit) {
    myMatchesLimit = matchesLimit;
  }

  public @Nullable FindResult getCursor() {
    return myCursor;
  }

  public int getCursorVisualIndex() {
    return myCursor != null ? myOccurrences.indexOf(myCursor) + 1 : -1;
  }

  public @NotNull List<FindResult> getOccurrences() {
    return myOccurrences;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull Editor getEditor() {
    return myEditor;
  }

  public void clear() {
    searchCompleted(new ArrayList<>(), getEditor(), null, false, null, getStamp());
  }

  @NotNull
  ActionCallback updateThreadSafe(@NotNull FindModel findModel, boolean toChangeSelection, @Nullable TextRange next, int stamp) {
    if (myDisposed) return ActionCallback.DONE;

    ActionCallback result = new ActionCallback();
    Editor editor = getEditor();

    updatePreviousFindModel(findModel);
    SearchArea searchArea = getSearchArea(editor, findModel);

    List<FindResult> results = new ArrayList<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      Project project = getProject();
      if (myDisposed || project.isDisposed()) return;

      int[] starts = searchArea.startOffsets;
      int[] ends = searchArea.endOffsets;
      for (int i = 0; i < starts.length; ++i) {
        findInRange(new TextRange(starts[i], ends[i]), editor, findModel, results);
      }

      long documentTimeStamp = editor.getDocument().getModificationStamp();

      UIUtil.invokeLaterIfNeeded(() -> {
        if (editor.getDocument().getModificationStamp() == documentTimeStamp) {
          searchCompleted(results, editor, findModel, toChangeSelection, next, stamp);
          result.setDone();
        }
        else {
          result.setRejected();
        }
      });
    });
    return result;
  }

  private void updatePreviousFindModel(@NotNull FindModel model) {
    FindModel prev = FindManager.getInstance(getProject()).getPreviousFindModel();
    if (prev == null) {
      prev = new FindModel();
    }
    if (!model.getStringToFind().isEmpty()) {
      prev.copyFrom(model);
      FindManager.getInstance(getProject()).setPreviousFindModel(prev);
    }
  }

  public record SearchArea(int[] startOffsets, int[] endOffsets) {
    public static SearchArea create(int[] startOffsets, int[] endOffsets) {
      check(startOffsets, endOffsets);
      return new SearchArea(startOffsets, endOffsets);
    }

    private static void check(int[] startOffsets, int[] endOffsets) {
      if (startOffsets.length != endOffsets.length) {
        throw new IllegalArgumentException("startOffsets and endOffsets must have the same length");
      }
    }


    /**
     * Merges the given {@link SearchArea} with the current one.
     */
    public @NotNull SearchArea union(@NotNull SearchArea area) {
      int[] mergedStartOffsets = ArrayUtil.mergeArrays(startOffsets, area.startOffsets);
      int[] mergedEndOffsets = ArrayUtil.mergeArrays(endOffsets, area.endOffsets);
      Arrays.sort(mergedStartOffsets);
      Arrays.sort(mergedEndOffsets);

      final IntList resultStartOffsets = new IntArrayList(mergedStartOffsets.length);
      final IntList resultEndOffsets = new IntArrayList(mergedStartOffsets.length);

      new Object() {
        int counter = 0;
        int startsIndex = 0;
        int endsIndex = 0;

        void run() {
          while (startsIndex < mergedStartOffsets.length ||
                 endsIndex < mergedEndOffsets.length) {
            if (endsIndex == mergedEndOffsets.length) {
              Logger.getInstance(SearchArea.class).error(String.format("Merging invalid SearchArea: %s - %s", this, area));
              nextStart();
            }
            else if (startsIndex == mergedStartOffsets.length) {
              nextEnd();
            }
            else {
              int start = mergedStartOffsets[startsIndex];
              int end = mergedEndOffsets[endsIndex];
              if (start <= end) {
                nextStart();
              }
              else {
                nextEnd();
              }
            }
          }
        }

        void nextStart() {
          if (counter == 0) {
            int startOffset = mergedStartOffsets[startsIndex];
            resultStartOffsets.add(startOffset);
          }
          counter++;
          startsIndex++;
        }

        void nextEnd() {
          counter--;
          if (counter == 0) {
            int endOffset = mergedEndOffsets[endsIndex];
            resultEndOffsets.add(endOffset);
          }
          if (counter < 0) {
            Logger.getInstance(SearchArea.class).error(String.format("Merging invalid SearchArea: %s - %s", this, area));
          }
          endsIndex++;
        }
      }.run();

      return create(resultStartOffsets.toIntArray(), resultEndOffsets.toIntArray());
    }
  }

  private @NotNull SearchArea getSearchArea(@NotNull Editor editor, @NotNull FindModel findModel) {
    SearchArea searchArea;
    if (ApplicationManager.getApplication().isDispatchThread()) {
      searchArea = getLocalSearchArea(editor, findModel);
    }
    else {
      CompletableFuture<SearchArea> future = new CompletableFuture<>();
      try {
        SwingUtilities.invokeAndWait(() -> {
          var result = getLocalSearchArea(editor, findModel);
          future.complete(result);
        });
      }
      catch (InterruptedException | InvocationTargetException ignore) {
      }
      searchArea = future.getNow(null);
    }
    if (searchArea != null && searchArea.startOffsets.length > 0) {
      return searchArea;
    }
    else {
      return globalSearchArea;
    }
  }

  @RequiresEdt
  protected @Nullable SearchArea getLocalSearchArea(@NotNull Editor editor, @NotNull FindModel findModel) {
    SearchArea searchArea = null;
    for (EditorSearchAreaProvider provider : EditorSearchAreaProvider.getEnabled(editor, findModel)) {
      SearchArea searchAreaFromEP = provider.getSearchArea(editor, findModel);
      if (searchAreaFromEP == null) continue;

      if (searchArea == null) {
        searchArea = searchAreaFromEP;
      }
      else {
        searchArea = searchArea.union(searchAreaFromEP);
      }
    }

    return searchArea;
  }

  private static class EditorSelectionSearchAreaProvider implements EditorSearchAreaProvider {
    @Override
    public boolean isEnabled(@NotNull Editor editor, @NotNull FindModel findModel) {
      return !findModel.isGlobal();
    }

    @Override
    public @Nullable SearchArea getSearchArea(@NotNull Editor editor, @NotNull FindModel findModel) {
      SelectionModel selection = editor.getSelectionModel();
      return SearchArea.create(selection.getBlockSelectionStarts(), selection.getBlockSelectionEnds());
    }
  }

  private void findInRange(@NotNull TextRange range, @NotNull Editor editor, @NotNull FindModel findModel, @NotNull List<? super FindResult> results) {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());

    // Document can change even while we're holding read lock (example case - console), so we're taking an immutable snapshot of text here
    CharSequence charSequence = editor.getDocument().getImmutableCharSequence();

    int offset = range.getStartOffset();
    int maxOffset = Math.min(range.getEndOffset(), charSequence.length());
    FindManager findManager = FindManager.getInstance(getProject());

    while (offset < maxOffset) {
      FindResult result;
      try {
        CharSequence bombedCharSequence = StringUtil.newBombedCharSequence(charSequence, 3000);
        result = findManager.findString(bombedCharSequence, offset, findModel, virtualFile);
        ((StringUtil.BombedCharSequence)bombedCharSequence).defuse();
      }
      catch(PatternSyntaxException | ProcessCanceledException e) {
        result = null;
      }
      if (result == null || !result.isStringFound()) break;
      int newOffset = result.getEndOffset();
      if (newOffset > maxOffset) break;
      if (offset == newOffset) {
        offset++; // skip zero-width result
      }
      else {
        offset = newOffset;
      }
      results.add(result);
    }
  }

  public void dispose() {
    myDisposed = true;
    myEditor.getCaretModel().removeCaretListener(this);
    myEditor.getDocument().removeDocumentListener(this);
  }

  private void searchCompleted(@NotNull List<FindResult> occurrences, @NotNull Editor editor, @Nullable FindModel findModel,
                               boolean toChangeSelection, @Nullable TextRange next, int stamp) {
    if (stamp < myLastUpdatedStamp){
      return;
    }
    myLastUpdatedStamp = stamp;
    if (editor != getEditor() || myDisposed || editor.isDisposed()) {
      return;
    }
    setUpdating(false);
    myOccurrences = occurrences;
    TextRange oldCursorRange = myCursor;
    myOccurrences.sort(Comparator.comparingInt(TextRange::getStartOffset));

    myFindModel = findModel;
    myDocumentTimestamp = myEditor.getDocument().getModificationStamp();
    updateCursor(oldCursorRange, next);
    updateExcluded();
    notifyChanged();
    if (myCursor == null || !myCursor.equals(oldCursorRange)) {
      if (toChangeSelection) {
        updateSelection(true, true, true);
      }
      notifyCursorMoved();
    }
    notifyUpdateFinished();
    Direction dir = myPendingSearch;
    if (dir != null && next == null) {
      if (dir == Direction.DOWN) {
        nextOccurrence(false);
      }
      else {
        prevOccurrence(false);
      }
    }
    myPendingSearch = null;
  }

  private void updateSelection(boolean removePreviousSelection, boolean removeAllPreviousSelections, boolean adjustScrollPosition) {
    for (SearchResultsListener listener : myListeners) {
      listener.beforeSelectionUpdate();
    }
    try {
      mySelectionManager.updateSelection(removePreviousSelection, removeAllPreviousSelections, adjustScrollPosition);
    }
    finally {
      for (SearchResultsListener listener : myListeners) {
        listener.afterSelectionUpdate();
      }
    }
  }

  private void notifyUpdateFinished() {
    for (SearchResultsListener listener : myListeners) {
      listener.updateFinished();
    }
  }

  private void updateExcluded() {
    Set<RangeMarker> invalid = new HashSet<>();
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
          if (justReplaced) {
            nextOccurrence(false, next, false, true, false);
          }
          else {
            myCursor = oldCursorRange == null ? firstOccurrenceAtOrAfterCaret() : firstOccurrenceAfterCaret();
          }
        }
        else {
          myCursor = null;
        }
      }
    }
    if (!justReplaced && myCursor == null && hasMatches()) {
      nextOccurrence(true, oldCursorRange, false, false, false);
    }
    if (toPush && myCursor != null){
      push();
    }
  }

  private boolean repairCursorFromStack() {
    if (myCursorPositions.size() >= 2) {
      Pair<FindModel, FindResult> oldPosition = myCursorPositions.get(myCursorPositions.size() - 2);
      if (oldPosition.first.equals(myFindModel)) {
        FindResult newCursor;
        if ((newCursor = findOccurrenceEqualTo(oldPosition.second)) != null) {
          myCursorPositions.pop();
          myCursor = newCursor;
          return true;
        }
      }
    }
    return false;
  }

  private @Nullable FindResult findOccurrenceEqualTo(FindResult occurrence) {
    for (FindResult findResult : myOccurrences) {
      if (findResult.equals(occurrence)) {
        return findResult;
      }
    }
    return null;
  }

  protected @Nullable FindResult firstOccurrenceAtOrAfterCaret() {
    FindResult occurrence = getOccurrenceAtCaret();
    if (occurrence != null) return occurrence;
    occurrence = getFirstOccurrenceInSelection();
    if (occurrence != null) return occurrence;
    return firstOccurrenceAfterCaret();
  }

  public @Nullable FindResult getOccurrenceAtCaret() {
    int offset = getEditor().getCaretModel().getOffset();
    return ContainerUtil.find(myOccurrences, occurrence -> occurrence.containsOffset(offset));
  }

  private @Nullable FindResult getFirstOccurrenceInSelection() {
    TextRange selection = getEditor().getCaretModel().getCurrentCaret().getSelectionRange();
    return ContainerUtil.find(myOccurrences, occurrence -> selection.contains(occurrence));
  }

  private void notifyChanged() {
    for (SearchResultsListener listener : myListeners) {
      listener.searchResultsUpdated(this);
    }
  }

  static boolean insideVisibleArea(Editor e, TextRange r) {
    int startOffset = r.getStartOffset();
    if (startOffset > e.getDocument().getTextLength()) return false;
    Rectangle visibleArea = e.getScrollingModel().getVisibleArea();
    Point point = e.logicalPositionToXY(e.offsetToLogicalPosition(startOffset));

    return visibleArea.contains(point);
  }

  public @Nullable FindResult firstOccurrenceBeforeCaret() {
    int offset = getEditor().getCaretModel().getOffset();
    return firstOccurrenceBeforeOffset(offset);
  }

  private @Nullable FindResult firstOccurrenceBeforeOffset(int offset) {
    for (int i = getOccurrences().size()-1; i >= 0; --i) {
      if (getOccurrences().get(i).getEndOffset() < offset) {
        return getOccurrences().get(i);
      }
    }
    return null;
  }

  public @Nullable FindResult firstOccurrenceAfterCaret() {
    int caret = myEditor.getCaretModel().getOffset();
    return firstOccurrenceAfterOffset(caret);
  }

  private @Nullable FindResult firstOccurrenceAfterOffset(int offset) {
    FindResult afterCaret = null;
    for (FindResult occurrence : getOccurrences()) {
      if (offset <= occurrence.getStartOffset() && (afterCaret == null || occurrence.getStartOffset() < afterCaret.getStartOffset())) {
        afterCaret = occurrence;
      }
    }
    return afterCaret;
  }

  private boolean tryToRepairOldCursor(@Nullable TextRange oldCursorRange) {
    if (oldCursorRange == null) return false;
    FindResult mayBeOldCursor = null;
    for (FindResult searchResult : getOccurrences()) {
      if (searchResult.intersects(oldCursorRange)) {
        mayBeOldCursor = searchResult;
      }
      if (searchResult.getStartOffset() == oldCursorRange.getStartOffset()) {
        break;
      }
    }
    if (mayBeOldCursor != null) {
      myCursor = mayBeOldCursor;
      return true;
    }
    return false;
  }

  private @Nullable FindResult prevOccurrence(TextRange range) {
    for (int i = getOccurrences().size() - 1; i >= 0; --i) {
      FindResult occurrence = getOccurrences().get(i);
      if (occurrence.getEndOffset() <= range.getStartOffset())  {
        return occurrence;
      }
    }
    return null;
  }

  private @Nullable FindResult nextOccurrence(TextRange range) {
    for (FindResult occurrence : getOccurrences()) {
      if (occurrence.getStartOffset() >= range.getEndOffset()) {
        return occurrence;
      }
    }
    return null;
  }

  public void prevOccurrence(boolean findSelected) {
    if (findSelected) {
      if (mySelectionManager.removeCurrentSelection()) {
        myCursor = firstOccurrenceAtOrAfterCaret();
      }
      else {
        myCursor = null;
      }
      notifyCursorMoved();
    }
    else {
      if (myFindModel == null) {
        myPendingSearch = Direction.UP;
        return;
      }
      boolean processFromTheBeginning = false;
      if (myNotFoundState) {
        myNotFoundState = false;
        processFromTheBeginning = true;
      }
      FindResult next = null;
      if (!myFindModel.isGlobal()) {
        if (myCursor != null) {
          next = prevOccurrence(myCursor);
        }
      }
      else {
        next = firstOccurrenceBeforeCaret();
      }
      if (next == null) {
        if (processFromTheBeginning) {
          if (hasMatches()) {
            next = getOccurrences().get(getOccurrences().size() - 1);
          }
        }
        else {
          setNotFoundState(false);
        }
      }

      if (next != null) {
        moveCursorTo(next, false, true);
      }
    }
    push();
  }

  private void push() {
    myCursorPositions.push(Pair.create(myFindModel, myCursor));
  }

  public void nextOccurrence(boolean retainOldSelection) {
    if (myFindModel == null) {
      myPendingSearch = Direction.DOWN;
      return;
    }
    nextOccurrence(false, myCursor, true, false, retainOldSelection);
    push();
  }

  private void nextOccurrence(boolean processFromTheBeginning,
                              TextRange cursor,
                              boolean toNotify,
                              boolean justReplaced,
                              boolean retainOldSelection) {
    if (myNotFoundState) {
      myNotFoundState = false;
      processFromTheBeginning = true;
    }
    FindResult next;
    if ((!myFindModel.isGlobal() || justReplaced) && cursor != null) {
      next = nextOccurrence(cursor);
    }
    else {
      next = firstOccurrenceAfterCaret();
    }
    if (next == null) {
      if (processFromTheBeginning) {
        if (hasMatches()) {
          next = getOccurrences().get(0);
        }
      }
      else {
        setNotFoundState(true);
      }
    }
    if (toNotify) {
      if (next != null) {
        moveCursorTo(next, retainOldSelection, true);
      }
    }
    else {
      myCursor = next;
    }
  }

  private void moveCursorTo(@NotNull FindResult next, boolean retainOldSelection, boolean adjustScrollPosition) {
    retainOldSelection &= myCursor != null && mySelectionManager.isSelected(myCursor);
    myCursor = next;
    updateSelection(!retainOldSelection, false, adjustScrollPosition);
    notifyCursorMoved();
  }

  private void notifyCursorMoved() {
    for (SearchResultsListener listener : myListeners) {
      listener.cursorMoved();
      listener.searchResultsUpdated(this);
    }
  }

  public boolean isUpToDate() {
    return myDocumentTimestamp == myEditor.getDocument().getModificationStamp();
  }

  void setUpdating(boolean value) {
    myUpdating = value;
  }

  public boolean isUpdating() {
    return myUpdating;
  }
}
