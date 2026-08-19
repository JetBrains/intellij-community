// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl.livePreview;


import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.find.FindUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.SwingUtilities;
import java.awt.Point;
import java.awt.Rectangle;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    if (myEditor.getCaretModel().getCaretCount() == 1) {
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

  private static final long CHUNK_TIME_BUDGET_MS = 50;

  /**
   * Where a chunked scan of a {@link SearchArea} resumes: the index of the range being scanned, and the offset inside
   * the document to continue that range from.
   */
  private record SearchPosition(int rangeIndex, int offset) {}

  /**
   * One chunk of a chunked search: the occurrences it found, where the next chunk has to resume ({@code null} once the
   * whole {@link SearchArea} is scanned), and the document stamp the chunk was computed against.
   */
  private record SearchChunk(@NotNull List<FindResult> results, @Nullable SearchPosition resumeAt, long documentTimeStamp) {}

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

  private int myChunkMatchLimit = Integer.MAX_VALUE;
  private @Nullable Runnable myChunkHook;

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

    /**
     * Reports the occurrences a still running search has just appended to {@link #getOccurrences()}, so that partial
     * results become visible before the search is over. A listener that can render the new occurrences on their own
     * should do so here instead of rescanning the ones it has already seen; the default refreshes everything.
     */
    default void searchResultsAppended(@NotNull SearchResults sr, @NotNull List<FindResult> added) {
      searchResultsUpdated(sr);
    }

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

  /** Drops every occurrence, as if a search that found nothing had just completed. Has to be called on the EDT. */
  public void clear() {
    myLastUpdatedStamp = getStamp();
    if (myDisposed || getEditor().isDisposed()) {
      return;
    }
    searchFinished(false, null, searchStarted(null));
  }

  /**
   * Runs the search in chunks, publishing each one as soon as it is found so that a slow search shows its matches
   * while it is still running instead of staying blank until the very end.
   * <p>
   * Every chunk is a separate cancellable read action over a {@linkplain #findNextChunk pure} step function, so the
   * read lock is only ever held for one chunk at a time and a long search cannot stall a write action.
   *
   * @return a callback that is done once the results are applied, and rejected when the search has to be run again
   *         because the document changed or a write action took priority. It is always completed on the EDT, whichever
   *         thread the search itself ran on - see {@link #rejectLater}.
   */
  @NotNull
  ActionCallback updateThreadSafe(@NotNull FindModel findModel, boolean toChangeSelection, @Nullable TextRange next, int stamp) {
    if (myDisposed || getProject().isDisposed()) return ActionCallback.DONE;

    ActionCallback result = new ActionCallback();
    Editor editor = getEditor();

    updatePreviousFindModel(findModel);
    SearchArea searchArea = getSearchArea(editor, findModel);

    SearchStreamer streamer = new SearchStreamer(editor, findModel, toChangeSelection, next, stamp, result);
    long budget = chunkTimeBudgetMs();
    int maxMatches = chunkMatchLimit();
    SearchPosition from = new SearchPosition(0, searchArea.startOffsets[0]);
    long documentTimeStamp = -1;
    boolean first = true;

    while (from != null) {
      SearchChunk chunk;
      try {
        if (myChunkHook != null) myChunkHook.run();
        chunk = computeChunk(editor, findModel, searchArea, from, maxMatches, budget);
      }
      catch (@SuppressWarnings("IncorrectCancellationExceptionHandling") ProcessCanceledException e) {
        // A write action took priority over this chunk's read action: the read job is cancelled but this thread's own
        // job is not, so the re-check below returns and the search is simply run again. Nothing has been half-applied,
        // because the step function keeps its results to itself until it returns them.
        ProgressManager.checkCanceled();
        rejectLater(result);
        return result;
      }
      // Leave the callback uncompleted on disposal: there is nothing left to apply, and nothing to retry either.
      if (myDisposed || getProject().isDisposed()) return result;
      if (!first && chunk.documentTimeStamp() != documentTimeStamp) {
        // The document changed between two chunks, so the offsets published so far are stale.
        rejectLater(result);
        return result;
      }
      documentTimeStamp = chunk.documentTimeStamp();
      from = chunk.resumeAt();
      streamer.publish(chunk.results(), first, from == null, documentTimeStamp);
      first = false;
    }
    return result;
  }

  /**
   * Rejects on the EDT under write intent, which is where {@link SearchStreamer#apply} would have completed the
   * callback had the search got that far.
   * <p>
   * Which thread the callback completes on is part of the contract rather than an accident of where a chunk happened
   * to fail: the caller retries the whole search from its rejection handler, and that path drives the find toolbar.
   * Going through the event queue also queues the rejection behind the chunks already published, so the results found
   * so far are applied before the search starts over.
   */
  private static void rejectLater(@NotNull ActionCallback callback) {
    UIUtil.invokeLaterIfNeeded(() -> WriteIntentReadAction.run(callback::setRejected));
  }

  /**
   * Whether this thread can hand the read lock over to a pending write action at a chunk boundary.
   * <p>
   * It cannot on the EDT, which is where a write action would be waiting, nor under a read lock this thread already
   * holds and will keep holding either way - and {@link ReadAction#computeCancellableUnsafe} is declared
   * {@link com.intellij.util.concurrency.annotations.RequiresBackgroundThread} for exactly that reason.
   */
  private static boolean canYieldToWriteAction() {
    Application application = ApplicationManager.getApplication();
    return !application.isDispatchThread() && !application.isReadAccessAllowed();
  }

  /**
   * Whether a search runs in chunks at all. Where the read lock cannot be yielded, chunking would only cost
   * publications, so the whole search runs as one chunk exactly as it did before the search became chunked.
   */
  private static boolean isChunked() {
    return Registry.is("ide.find.incremental.results") && canYieldToWriteAction();
  }

  /** How long a single chunk may hold the read lock. */
  private static long chunkTimeBudgetMs() {
    return isChunked() ? CHUNK_TIME_BUDGET_MS : Long.MAX_VALUE;
  }

  /**
   * How many occurrences a single chunk may carry.
   * <p>
   * The time budget alone does not bound a chunk usefully: a plain-text scan over a big file finds tens of thousands of
   * matches well inside it, and applying them is the expensive half - every occurrence becomes a range highlighter on
   * the EDT. Capping the matches too keeps each publication's EDT event short, which is the whole point of streaming
   * the results in the first place.
   */
  private int chunkMatchLimit() {
    if (myChunkMatchLimit != Integer.MAX_VALUE) return myChunkMatchLimit; // a test drives the chunk boundaries itself
    return isChunked() ? LivePreviewController.MATCHES_LIMIT : Integer.MAX_VALUE;
  }

  private @NotNull SearchChunk computeChunk(@NotNull Editor editor,
                                            @NotNull FindModel findModel,
                                            @NotNull SearchArea searchArea,
                                            @NotNull SearchPosition from,
                                            int maxMatches,
                                            long budgetMs) {
    return canYieldToWriteAction()
           ? ReadAction.computeCancellableUnsafe(() -> findNextChunk(editor, findModel, searchArea, from, maxMatches, budgetMs))
           : ReadAction.computeBlocking(() -> findNextChunk(editor, findModel, searchArea, from, maxMatches, budgetMs));
  }

  /**
   * Applies the chunks of one streamed search to the state of the enclosing {@link SearchResults}.
   * <p>
   * The background driver only calls {@link #publish}; everything else, including the fields of this class, is touched
   * on the EDT alone, and the chunks are applied in the order they were published.
   */
  private final class SearchStreamer {
    private final @NotNull Editor myTargetEditor;
    private final @NotNull FindModel myModel;
    private final boolean myToChangeSelection;
    private final @Nullable TextRange myNext;
    private final int myStamp;
    private final @NotNull ActionCallback myCallback;

    private @Nullable TextRange myOldCursorRange;

    private SearchStreamer(@NotNull Editor editor,
                           @NotNull FindModel model,
                           boolean toChangeSelection,
                           @Nullable TextRange next,
                           int stamp,
                           @NotNull ActionCallback callback) {
      myTargetEditor = editor;
      myModel = model;
      myToChangeSelection = toChangeSelection;
      myNext = next;
      myStamp = stamp;
      myCallback = callback;
    }

    void publish(@NotNull List<FindResult> chunk, boolean first, boolean last, long documentTimeStamp) {
      UIUtil.invokeLaterIfNeeded(() -> WriteIntentReadAction.run(() -> apply(chunk, first, last, documentTimeStamp)));
    }

    @RequiresEdt
    private void apply(@NotNull List<FindResult> chunk, boolean first, boolean last, long documentTimeStamp) {
      if (myStamp < myLastUpdatedStamp) {
        return;
      }
      myLastUpdatedStamp = myStamp;
      if (myTargetEditor != getEditor() || myDisposed || myTargetEditor.isDisposed()) {
        return;
      }

      if (first) {
        myOldCursorRange = searchStarted(myModel);
      }
      searchAdvanced(chunk, first, last);

      if (last) {
        if (myTargetEditor.getDocument().getModificationStamp() == documentTimeStamp) {
          searchFinished(myToChangeSelection, myNext, myOldCursorRange);
          myCallback.setDone();
        }
        else {
          // The last chunk stays unpublished, and the occurrences of this search have already replaced the ones the
          // listeners are showing, so tell them what they are holding before the retry that follows gets to run.
          notifyChanged();
          myCallback.setRejected();
        }
      }
    }
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
          var result = ReadAction.computeBlocking(() -> getLocalSearchArea(editor, findModel));
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

  /**
   * Scans {@code searchArea} forward from {@code from}, stopping as soon as the area is exhausted, {@code maxMatches}
   * occurrences are collected, or {@code budgetMs} milliseconds have passed.
   * <p>
   * This is the single step of a chunked search, and it is deliberately pure: it allocates its own result list and
   * reads no mutable state of this {@link SearchResults}. That is what makes a chunk safe to run inside a cancellable
   * read action - an attempt that loses to a write action leaves nothing half-written behind and can just be repeated.
   *
   * @param maxMatches how many occurrences one chunk may collect before it yields; only tests pass anything but
   *                   {@link Integer#MAX_VALUE} here
   */
  @RequiresReadLock
  private @NotNull SearchChunk findNextChunk(@NotNull Editor editor,
                                             @NotNull FindModel findModel,
                                             @NotNull SearchArea searchArea,
                                             @NotNull SearchPosition from,
                                             int maxMatches,
                                             long budgetMs) {
    int[] starts = searchArea.startOffsets;
    int[] ends = searchArea.endOffsets;
    long deadline = budgetMs == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + budgetMs;
    List<FindResult> results = new ArrayList<>();

    int offset = from.offset();
    for (int i = from.rangeIndex(); i < starts.length; ++i) {
      if (i != from.rangeIndex()) {
        offset = starts[i];
      }
      int resumeFrom = findInRange(new TextRange(offset, ends[i]), editor, findModel, results, maxMatches, deadline);
      if (resumeFrom >= 0) {
        return new SearchChunk(results, new SearchPosition(i, resumeFrom), editor.getDocument().getModificationStamp());
      }
    }
    return new SearchChunk(results, null, editor.getDocument().getModificationStamp());
  }

  /**
   * Collects the occurrences of {@code findModel} inside {@code range} into {@code results}, yielding early once
   * {@code results} holds {@code maxMatches} occurrences or {@code deadline} has passed.
   * <p>
   * A cancellation of the enclosing read action is left to propagate: {@link FindManager#findString} checks for it
   * between search attempts, and for a regular expression also from inside the match itself, so that the search is
   * retried rather than published with the occurrences it had found so far.
   *
   * @return the offset this range has to be resumed from, or {@code -1} once the range is scanned to its end
   */
  private int findInRange(@NotNull TextRange range,
                          @NotNull Editor editor,
                          @NotNull FindModel findModel,
                          @NotNull List<? super FindResult> results,
                          int maxMatches,
                          long deadline) {
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());

    // Document can change even while we're holding read lock (example case - console), so we're taking an immutable snapshot of text here
    CharSequence charSequence = editor.getDocument().getImmutableCharSequence();

    int offset = range.getStartOffset();
    int maxOffset = Math.min(range.getEndOffset(), charSequence.length());
    FindManager findManager = FindManager.getInstance(getProject());
    boolean timed = deadline != Long.MAX_VALUE; // an unchunked search has no deadline, and reads no clock per match

    while (offset < maxOffset) {
      if (results.size() >= maxMatches || (timed && System.currentTimeMillis() >= deadline)) return offset;

      FindResult result;
      try {
        result = findManager.findString(charSequence, offset, findModel, virtualFile);
      }
      catch (PatternSyntaxException e) {
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
    return -1;
  }

  /**
   * Forces a search to publish a chunk every {@code maxMatches} occurrences, so that tests can drive the chunked path
   * without depending on how long a search happens to take. Overrides {@link LivePreviewController#MATCHES_LIMIT}, and applies even
   * where a search would otherwise run {@linkplain #isChunked() unchunked}, as it does on the EDT.
   */
  @TestOnly
  @ApiStatus.Internal
  public void setChunkMatchLimit(int maxMatches) {
    myChunkMatchLimit = maxMatches;
  }

  /**
   * Runs {@code hook} once per chunk, where the chunk is computed, so that tests can make a search fail partway
   * through at a defined point rather than by racing it. A hook that throws {@link ProcessCanceledException} stands in
   * for a chunk whose read action lost to a write action.
   */
  @TestOnly
  @ApiStatus.Internal
  public void setChunkHook(@Nullable Runnable hook) {
    myChunkHook = hook;
  }

  public void dispose() {
    myDisposed = true;
    myEditor.getCaretModel().removeCaretListener(this);
    myEditor.getDocument().removeDocumentListener(this);
  }

  /**
   * Drops the results of the previous search and takes the model the new one runs with.
   * <p>
   * The model has to be in place before the first chunk is published rather than once the search is over: everything
   * that reacts to an occurrence reads the model to interpret it, from {@link SelectionManager} to the status text of
   * the find bar, so an occurrence that is visible while the model is not yet is an occurrence nobody can act on.
   *
   * @return the cursor range {@link #searchFinished} should try to restore afterwards. It has to be taken now, because
   *         the chunks published while the search runs must not be indexed against a cursor of the previous search.
   */
  @RequiresEdt
  private @Nullable TextRange searchStarted(@Nullable FindModel findModel) {
    TextRange oldCursorRange = myCursor;
    myOccurrences = new ArrayList<>();
    myCursor = null;
    myFindModel = findModel;
    return oldCursorRange;
  }

  /**
   * Appends one chunk of occurrences so that the listeners can render it before the search is over.
   * <p>
   * The first chunk is reported as a full update, which is what makes the listeners drop whatever the previous search
   * left behind; the ones after it are reported as appends, which a listener can apply without revisiting the
   * occurrences it has already seen.
   * <p>
   * The last chunk is not reported here at all, because {@link #searchFinished} publishes the settled result set in
   * full immediately afterwards: a listener about to be handed every occurrence gains nothing from having been handed
   * the tail of them a moment earlier. It costs, though - a full update walks every occurrence on screen, turning each
   * one into a range highlighter - and a search that fits in a single chunk, which is the common case, would otherwise
   * pay for that walk one extra time. It is what
   * {@code FindInEditorPerformanceTest#testEditingWithSearchResultsShown} pays per keystroke, and skipping it there is
   * worth ~20% of that benchmark.
   */
  @RequiresEdt
  private void searchAdvanced(@NotNull List<FindResult> chunk, boolean first, boolean last) {
    myOccurrences.addAll(chunk);
    if (last) {
      return;
    }
    if (first) {
      notifyChanged();
    }
    else if (!chunk.isEmpty()) {
      notifyAppended(chunk);
    }
  }

  /** Settles the cursor, the selection and the listeners once every chunk of a search has been applied. */
  @RequiresEdt
  private void searchFinished(boolean toChangeSelection, @Nullable TextRange next, @Nullable TextRange oldCursorRange) {
    setUpdating(false);
    myOccurrences.sort(Comparator.comparingInt(TextRange::getStartOffset));

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

  private void notifyAppended(@NotNull List<FindResult> added) {
    for (SearchResultsListener listener : myListeners) {
      listener.searchResultsAppended(this, added);
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
