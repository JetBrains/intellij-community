// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl.livePreview;

import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.SelectionEvent;
import com.intellij.openapi.editor.event.SelectionListener;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.util.SingleEdtTaskScheduler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.PositionTracker;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Point;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class LivePreview implements SearchResults.SearchResultsListener, SelectionListener, DocumentListener, EditorColorsListener {
  private static final Key<RangeHighlighter> IN_SELECTION_KEY = Key.create("LivePreview.IN_SELECTION_KEY");

  private final Disposable myDisposable = Disposer.newDisposable("livePreview");
  private boolean mySuppressedUpdate = false;

  private static final Key<Boolean> MARKER_USED = Key.create("LivePreview.MARKER_USED");
  private static final Key<Boolean> SEARCH_MARKER = Key.create("LivePreview.SEARCH_MARKER");

  public static PrintStream ourTestOutput;
  private String myReplacementPreviewText;
  private static boolean NotFound;

  private final List<RangeHighlighter> myHighlighters = new ArrayList<>();
  /**
   * The occurrences that currently carry an {@link #IN_SELECTION_KEY} companion. Tracking them is what lets a selection
   * change touch only the occurrences it can actually affect, instead of asking every match on screen.
   */
  private final Set<RangeHighlighter> myInSelectionHighlighters = new HashSet<>();
  /**
   * Coalesces the in-selection refresh, which several listeners ask for in a row over one gesture and only the last
   * result of which is ever painted. One mouse-moved event of a drag selection asks for it five times: the drag sets
   * the selection, {@link SearchResults#caretPositionChanged} then collapses it onto the occurrence under the caret and
   * clears it again, each of which is a selection change, and the cursor it moved is reported twice over.
   */
  private final SingleEdtTaskScheduler myInSelectionUpdateAlarm = SingleEdtTaskScheduler.createSingleEdtTaskScheduler();
  private boolean myInSelectionUpdatePending;
  private RangeHighlighter myCursorHighlighter;
  private VisibleAreaListener myVisibleAreaListener;
  private Delegate myDelegate;
  private final SearchResults mySearchResults;
  private final LivePreviewPresentation myPresentation;
  private Balloon myReplacementBalloon;

  @Override
  public void selectionChanged(@NotNull SelectionEvent e) {
    requestInSelectionUpdate();
  }

  /**
   * Asks for the in-selection highlighting to be brought up to date once the gesture that changed the selection is over,
   * rather than once per change it makes along the way.
   */
  private void requestInSelectionUpdate() {
    myInSelectionUpdatePending = true;
    // Throttled, not debounced: a gesture that keeps changing the selection must still be caught up with promptly.
    myInSelectionUpdateAlarm.request(0, this::applyPendingInSelectionUpdate);
  }

  private void applyPendingInSelectionUpdate() {
    if (!myInSelectionUpdatePending) return;
    myInSelectionUpdatePending = false;
    updateInSelectionHighlighters();
  }

  /**
   * Applies a pending {@link #requestInSelectionUpdate} right now, for when the highlighting has to be up to date
   * before the end of the event it was requested in. Kept separate from the task the alarm runs, which must not cancel
   * the job it is itself running under.
   */
  private void flushInSelectionUpdate() {
    myInSelectionUpdateAlarm.cancel();
    applyPendingInSelectionUpdate();
  }

  public static void processNotFound() {
    NotFound = true;
  }

  @ApiStatus.Internal
  public interface Delegate {
    @NlsSafe @Nullable String getStringToReplace(@NotNull Editor editor, @Nullable FindResult findResult)
      throws FindManager.MalformedReplacementStringException;
  }

  @Override
  public void searchResultsUpdated(@NotNull SearchResults sr) {
    if (mySuppressedUpdate) {
      mySuppressedUpdate = false;
      return;
    }
    highlightUsages();
    updateCursorHighlighting();
  }

  @Override
  public void searchResultsAppended(@NotNull SearchResults sr, @NotNull List<FindResult> added) {
    if (!isBelowMatchesLimit()) {
      // Same call as a full update would make: past the limit nothing is highlighted at all.
      dropHighlighters();
      return;
    }
    // Only the appended occurrences can need a highlighter, and none of the existing ones can have become stale, so
    // there is nothing to look for among the occurrences already on screen. The same goes for the in-selection
    // highlighting: neither the selection nor the cursor moves while a search streams - the cursor is only settled once
    // the whole search is over - so the highlighters already on screen keep whatever they were given. Revisiting them
    // per chunk would make a streamed search quadratic in the number of matches. The cursor being unsettled is also why
    // there is no cursor highlighting to redo here.
    List<RangeHighlighter> newHighlighters = addNewHighlighters(added);
    myHighlighters.addAll(newHighlighters);
    updateInSelectionHighlighters(newHighlighters);
  }

  private void dumpState() {
    if (ApplicationManager.getApplication().isUnitTestMode() && ourTestOutput != null) {
      flushInSelectionUpdate(); // the dump is of the markup model, so everything owed to it has to be in place first
      dumpEditorMarkupAndSelection(ourTestOutput);
    }
  }

  private void dumpEditorMarkupAndSelection(PrintStream dumpStream) {
    dumpStream.println(mySearchResults.getFindModel());
    if (myReplacementPreviewText != null) {
      dumpStream.println("--");
      dumpStream.println("Replacement Preview: " + myReplacementPreviewText);
    }
    dumpStream.println("--");

    Editor editor = mySearchResults.getEditor();

    RangeHighlighter[] highlighters = editor.getMarkupModel().getAllHighlighters();
    Arrays.sort(highlighters, Segment.BY_START_OFFSET_THEN_END_OFFSET);
    List<Pair<Integer, Character>> ranges = new ArrayList<>();
    for (RangeHighlighter highlighter : highlighters) {
      ranges.add(new Pair<>(highlighter.getStartOffset(), '['));
      ranges.add(new Pair<>(highlighter.getEndOffset(), ']'));
    }

    SelectionModel selectionModel = editor.getSelectionModel();

    if (selectionModel.getSelectionStart() != selectionModel.getSelectionEnd()) {
      ranges.add(new Pair<>(selectionModel.getSelectionStart(), '<'));
      ranges.add(new Pair<>(selectionModel.getSelectionEnd(), '>'));
    }
    ranges.add(new Pair<>(-1, '\n'));
    ranges.add(new Pair<>(editor.getDocument().getTextLength() + 1, '\n'));
    ContainerUtil.sort(ranges, (pair, pair2) -> {
      int res = pair.first - pair2.first;
      if (res == 0) {

        Character c1 = pair.second;
        Character c2 = pair2.second;
        if (c1 == '<' && c2 == '[') {
          return 1;
        }
        else if (c1 == '[' && c2 == '<') {
          return -1;
        }
        return c1.compareTo(c2);
      }
      return res;
    });

    Document document = editor.getDocument();
    for (int i = 0; i < ranges.size()-1; ++i) {
      Pair<Integer, Character> pair = ranges.get(i);
      Pair<Integer, Character> pair1 = ranges.get(i + 1);
      dumpStream.print(pair.second + document.getText(TextRange.create(Math.max(pair.first, 0),
                                                                       Math.min(pair1.first, document.getTextLength()))));
    }
    dumpStream.println("\n--");

    if (NotFound) {
      dumpStream.println("Not Found");
      dumpStream.println("--");
      NotFound = false;
    }

    for (RangeHighlighter highlighter : highlighters) {
      dumpStream.println("highlighter: "+highlighter.getTextRange() + "; layer: "+highlighter.getLayer()+" : " + highlighter.getTextAttributes(editor.getColorsScheme()).getEffectType());
    }
    dumpStream.println("------------");
  }

  private void clearUnusedHighlighters() {
    myHighlighters.removeIf(h -> {
      if (h.getUserData(MARKER_USED) == null) {
        removeHighlighterWithDependent(h);
        return true;
      }
      else {
        h.putUserData(MARKER_USED, null);
        return false;
      }
    });
  }

  private void removeHighlighterWithDependent(@NotNull RangeHighlighter highlighter) {
    removeHighlighter(highlighter);
    myInSelectionHighlighters.remove(highlighter);
    RangeHighlighter additionalHighlighter = highlighter.getUserData(IN_SELECTION_KEY);
    if (additionalHighlighter != null) {
      removeHighlighter(additionalHighlighter);
    }
  }

  @Override
  public void cursorMoved() {
    requestInSelectionUpdate();
    updateCursorHighlighting();
  }

  @Override
  public void updateFinished() {
    dumpState();
  }

  @ApiStatus.Internal
  public void clearCursorHighlight() {
    hideBalloon();
    if (myCursorHighlighter != null) {
      removeHighlighter(myCursorHighlighter);
      myCursorHighlighter = null;
    }
  }

  private void updateCursorHighlighting() {
    clearCursorHighlight();

    final FindResult cursor = mySearchResults.getCursor();
    Editor editor = mySearchResults.getEditor();
    if (cursor != null && cursor.getEndOffset() <= editor.getDocument().getTextLength()) {
      myCursorHighlighter = addHighlighter(cursor.getStartOffset(), cursor.getEndOffset(), myPresentation.getCursorAttributes(),
                                           myPresentation.getCursorLayer());
      editor.getScrollingModel().runActionOnScrollingFinished(() -> showReplacementPreview());
    }
  }

  public LivePreview(@NotNull SearchResults searchResults, @NotNull LivePreviewPresentation presentation) {
    mySearchResults = searchResults;
    myPresentation = presentation;
    searchResultsUpdated(searchResults);
    searchResults.addListener(this);
    EditorUtil.addBulkSelectionListener(mySearchResults.getEditor(), this, myDisposable);
    ApplicationManager.getApplication().getMessageBus().connect(myDisposable).subscribe(EditorColorsManager.TOPIC, this);
  }

  @ApiStatus.Internal
  public Delegate getDelegate() {
    return myDelegate;
  }

  @ApiStatus.Internal
  public void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }

  @Override
  public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
    highlightUsages();
    updateCursorHighlighting();
  }

  public void dispose() {
    hideBalloon();

    myInSelectionUpdatePending = false;
    myInSelectionUpdateAlarm.dispose();

    dropHighlighters();

    if (myCursorHighlighter != null) {
      removeHighlighter(myCursorHighlighter);
    }
    myCursorHighlighter = null;

    Disposer.dispose(myDisposable);

    mySearchResults.removeListener(this);
  }

  private void highlightUsages() {
    // Only the in-selection tail is deferred: addNewHighlighters marks the highlighters it reused and
    // clearUnusedHighlighters drops the ones it did not, so those two have to stay together and stay synchronous, or a
    // chunk appended in between would be taken for a leftover of the previous search and removed.
    List<RangeHighlighter> newHighlighters = isBelowMatchesLimit()
                                             ? addNewHighlighters(mySearchResults.getOccurrences()) : Collections.emptyList();
    clearUnusedHighlighters();
    myHighlighters.addAll(newHighlighters);
    requestInSelectionUpdate();
  }

  private boolean isBelowMatchesLimit() {
    return mySearchResults.getMatchesCount() < mySearchResults.getMatchesLimit();
  }

  private void dropHighlighters() {
    for (RangeHighlighter h : myHighlighters) {
      removeHighlighterWithDependent(h);
    }
    myHighlighters.clear();
    myInSelectionHighlighters.clear();
  }

  private List<RangeHighlighter> addNewHighlighters(@NotNull List<FindResult> occurrences) {
    List<RangeHighlighter> newHighlighters = new ArrayList<>(occurrences.size());
    for (FindResult range : occurrences) {
      if (range.getEndOffset() > mySearchResults.getEditor().getDocument().getTextLength()) continue;
      TextAttributes attributes = createAttributes(range);
      RangeHighlighter existingHighlighter = findExistingHighlighter(range.getStartOffset(), range.getEndOffset(), attributes);
      if (existingHighlighter == null) {
        RangeHighlighter highlighter = addHighlighter(range.getStartOffset(), range.getEndOffset(),
                                                      attributes, myPresentation.getDefaultLayer());
        if (highlighter != null) {
          highlighter.putUserData(SEARCH_MARKER, Boolean.TRUE);
          newHighlighters.add(highlighter);
        }
      }
      else {
        existingHighlighter.putUserData(MARKER_USED, Boolean.TRUE);
      }
    }
    return newHighlighters;
  }

  private TextAttributes createAttributes(FindResult range) {
    if (mySearchResults.isExcluded(range)) {
      return myPresentation.getExcludedAttributes();
    }
    else if (range.isEmpty()) {
      return myPresentation.getEmptyRangeAttributes();
    }
    else {
      return myPresentation.getDefaultAttributes();
    }
  }

  private RangeHighlighter findExistingHighlighter(int startOffset, int endOffset, TextAttributes attributes) {
    MarkupModelEx markupModel = (MarkupModelEx)mySearchResults.getEditor().getMarkupModel();
    RangeHighlighter[] existing = new RangeHighlighter[1];
    markupModel.processRangeHighlightersOverlappingWith(startOffset, startOffset, highlighter -> {
      if (highlighter.getUserData(SEARCH_MARKER) != null &&
          highlighter.getStartOffset() == startOffset && highlighter.getEndOffset() == endOffset &&
          Objects.equals(highlighter.getTextAttributes(mySearchResults.getEditor().getColorsScheme()), attributes)) {
        existing[0] = highlighter;
        return false;
      }
      return true;
    });
    return existing[0];
  }

  /**
   * Brings the in-selection highlighting in line with a selection that has just changed.
   * <p>
   * Only two kinds of occurrence can need anything done to them: the ones the new selection covers, which the markup
   * model can hand over directly, and the ones that were covered by the previous selection, which are the ones already
   * tracked in {@link #myInSelectionHighlighters}. Everything else is left alone, so a selection change costs what the
   * selection covers rather than a walk over every match in the document - a mouse drag over a file with tens of
   * thousands of matches highlighted fires this on every mouse-moved event.
   */
  private void updateInSelectionHighlighters() {
    MarkupModelEx markupModel = (MarkupModelEx)mySearchResults.getEditor().getMarkupModel();
    SelectionModel selectionModel = mySearchResults.getEditor().getSelectionModel();
    int[] starts = selectionModel.getBlockSelectionStarts();
    int[] ends = selectionModel.getBlockSelectionEnds();
    TextRange cursor = mySearchResults.getCursor();

    // Collected rather than acted on inside the processor: that runs under the markup model lock, which forbids both
    // touching the model and doing any real work.
    Set<RangeHighlighter> covered = new HashSet<>();
    for (int i = 0; i < starts.length; ++i) {
      int selectionStart = starts[i];
      int selectionEnd = ends[i];
      markupModel.processRangeHighlightersOverlappingWith(selectionStart, selectionEnd, highlighter -> {
        if (highlighter.getUserData(SEARCH_MARKER) != null && isInSelection(highlighter, cursor, selectionStart, selectionEnd)) {
          covered.add(highlighter);
        }
        return true;
      });
    }

    // Over a copy: dropping the highlighting writes back to the tracking set.
    for (RangeHighlighter highlighter : new ArrayList<>(myInSelectionHighlighters)) {
      if (!covered.contains(highlighter)) {
        dropInSelectionHighlighting(highlighter);
      }
    }
    for (RangeHighlighter highlighter : covered) {
      addInSelectionHighlighting(highlighter);
    }
  }

  /**
   * The same update for a set of occurrences known up front, which is what a still running search appends. The
   * selection cannot have changed under a search - it is the occurrences that are new - so the ones already on screen
   * keep whatever they were given.
   */
  private void updateInSelectionHighlighters(@NotNull List<RangeHighlighter> highlighters) {
    SelectionModel selectionModel = mySearchResults.getEditor().getSelectionModel();
    int[] starts = selectionModel.getBlockSelectionStarts();
    int[] ends = selectionModel.getBlockSelectionEnds();
    TextRange cursor = mySearchResults.getCursor();

    for (RangeHighlighter highlighter : highlighters) {
      if (!highlighter.isValid()) continue;
      boolean needsAdditionalHighlighting = false;
      for (int i = 0; i < starts.length && !needsAdditionalHighlighting; ++i) {
        needsAdditionalHighlighting = isInSelection(highlighter, cursor, starts[i], ends[i]);
      }
      if (needsAdditionalHighlighting) {
        addInSelectionHighlighting(highlighter);
      }
      else {
        dropInSelectionHighlighting(highlighter);
      }
    }
  }

  /**
   * Whether an occurrence lies inside one selection range and so has to be shown as selected. The cursor is shown as
   * the cursor instead, and an occurrence that merely touches the edge of the selection is not inside it.
   */
  private static boolean isInSelection(@NotNull RangeHighlighter highlighter,
                                       @Nullable TextRange cursor,
                                       int selectionStart,
                                       int selectionEnd) {
    int start = highlighter.getStartOffset();
    int end = highlighter.getEndOffset();
    if (cursor != null && start == cursor.getStartOffset() && end == cursor.getEndOffset()) return false;
    return Math.max(selectionStart, start) <= Math.min(selectionEnd, end) && selectionEnd != start && end != selectionStart;
  }

  private void addInSelectionHighlighting(@NotNull RangeHighlighter highlighter) {
    if (highlighter.getUserData(IN_SELECTION_KEY) != null) return;
    RangeHighlighter additionalHighlighter = addHighlighter(highlighter.getStartOffset(), highlighter.getEndOffset(),
                                                            myPresentation.getSelectionAttributes(),
                                                            myPresentation.getDefaultLayer());
    if (additionalHighlighter == null) return; // the project is gone; nothing to track and nothing to remove later
    highlighter.putUserData(IN_SELECTION_KEY, additionalHighlighter);
    myInSelectionHighlighters.add(highlighter);
  }

  private void dropInSelectionHighlighting(@NotNull RangeHighlighter highlighter) {
    RangeHighlighter additionalHighlighter = highlighter.getUserData(IN_SELECTION_KEY);
    myInSelectionHighlighters.remove(highlighter);
    if (additionalHighlighter == null) return;
    removeHighlighter(additionalHighlighter);
    highlighter.putUserData(IN_SELECTION_KEY, null);
  }

  private void showReplacementPreview() {
    hideBalloon();
    if (!mySearchResults.isUpToDate()) return;
    final FindResult cursor = mySearchResults.getCursor();
    final Editor editor = mySearchResults.getEditor();
    final FindModel findModel = mySearchResults.getFindModel();
    if (myDelegate != null && cursor != null && findModel.isReplaceState() && findModel.isRegularExpressions()) {
      String replacementPreviewText;
      try {
        replacementPreviewText = myDelegate.getStringToReplace(editor, cursor);
      }
      catch (FindManager.MalformedReplacementStringException e) {
        return;
      }
      if (replacementPreviewText == null) {
        return;//malformed replacement string
      }
      if (!replacementPreviewText.equals(findModel.getStringToReplace()) ||
          Registry.is("ide.find.show.replacement.hint.for.simple.regexp")) {
        showBalloon(editor, replacementPreviewText);
      }
    }
  }

  private void showBalloon(Editor editor, @NotNull @NlsSafe String replacementPreviewText) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myReplacementPreviewText = replacementPreviewText;
      return;
    }

    myReplacementBalloon = UsagePreviewPanel.createPreviewBalloon(UsagePreviewPanel.createPreviewHtml(replacementPreviewText));
    EditorUtil.disposeWithEditor(editor, myReplacementBalloon);
    myReplacementBalloon.show(new ReplacementBalloonPositionTracker(editor), Balloon.Position.below);
  }

  private void hideBalloon() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myReplacementPreviewText = null;
      return;
    }

    if (myReplacementBalloon != null) {
      myReplacementBalloon.hide();
      myReplacementBalloon = null;
    }

    removeVisibleAreaListener();
  }

  private void removeVisibleAreaListener() {
    if (myVisibleAreaListener != null) {
      mySearchResults.getEditor().getScrollingModel().removeVisibleAreaListener(myVisibleAreaListener);
      myVisibleAreaListener = null;
    }
  }

  private RangeHighlighter addHighlighter(int startOffset, int endOffset, @NotNull TextAttributes attributes, int layer) {
    Project project = mySearchResults.getProject();
    if (project.isDisposed()) return null;
    var markupModel = mySearchResults.getEditor().getMarkupModel();
    var highlighter = markupModel.addRangeHighlighter(startOffset, endOffset, layer, attributes, HighlighterTargetArea.EXACT_RANGE);
    if (highlighter instanceof RangeHighlighterEx ex) ex.setVisibleIfFolded(true);
    return highlighter;
  }

  private void removeHighlighter(@NotNull RangeHighlighter highlighter) {
    Project project = mySearchResults.getProject();
    if (project.isDisposed()) return;
    mySearchResults.getEditor().getMarkupModel().removeHighlighter(highlighter);
  }

  private final class ReplacementBalloonPositionTracker extends PositionTracker<Balloon> {
    private final Editor myEditor;

    ReplacementBalloonPositionTracker(Editor editor) {
      super(editor.getContentComponent());
      myEditor = editor;

    }

    @Override
    public RelativePoint recalculateLocation(@NotNull Balloon balloon) {
      FindResult cursor = mySearchResults.getCursor();
      if (cursor == null) return null;
      final TextRange cur = cursor;
      int startOffset = cur.getStartOffset();
      int endOffset = cur.getEndOffset();

      if (endOffset > myEditor.getDocument().getTextLength()) {
        if (!balloon.isDisposed()) {
          requestBalloonHiding(balloon);
        }
        return null;
      }
      if (!SearchResults.insideVisibleArea(myEditor, cur)) {
        requestBalloonHiding(balloon);

        removeVisibleAreaListener();
        myVisibleAreaListener = e -> {
          if (SearchResults.insideVisibleArea(myEditor, cur)) showReplacementPreview();
        };
        myEditor.getScrollingModel().addVisibleAreaListener(myVisibleAreaListener);
      }

      Point startPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(startOffset));
      Point endPoint = myEditor.visualPositionToXY(myEditor.offsetToVisualPosition(endOffset));
      Point point = new Point((startPoint.x + endPoint.x)/2, endPoint.y + myEditor.getLineHeight());

      return new RelativePoint(myEditor.getContentComponent(), point);
    }
  }

  private static void requestBalloonHiding(Balloon balloon) {
    ApplicationManager.getApplication().invokeLater(() -> balloon.hide());
  }
}
