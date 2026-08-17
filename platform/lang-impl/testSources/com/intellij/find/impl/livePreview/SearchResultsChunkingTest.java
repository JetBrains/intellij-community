// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl.livePreview;

import com.intellij.find.FindModel;
import com.intellij.find.FindResult;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A search runs in chunks so that its matches can be shown while it is still running. These tests pin down that the
 * chunked walk finds exactly what a single-chunk one finds, and that the chunks really do reach the listeners one by
 * one. The chunk boundaries are forced by {@link SearchResults#setChunkMatchLimit} rather than by the time budget a
 * production search uses, so nothing here depends on how long a search happens to take.
 */
public class SearchResultsChunkingTest extends LightPlatformCodeInsightTestCase {
  private static final String TEXT = "ab cd ab cd ab cd ab cd ab";

  @Override
  protected boolean isRunInCommand() {
    return false;
  }

  public void testChunkedSearchFindsWhatAnUnchunkedOneFinds() {
    configureFromText(TEXT);
    assertEquals(runSearch("ab", Integer.MAX_VALUE).occurrences, runSearch("ab", 1).occurrences);
    assertEquals(runSearch("ab", Integer.MAX_VALUE).occurrences, runSearch("ab", 2).occurrences);
  }

  public void testChunkedRegexpSearchFindsWhatAnUnchunkedOneFinds() {
    configureFromText(TEXT);
    FindModel model = findModel("a.");
    model.setRegularExpressions(true);
    assertEquals(runSearch(model, Integer.MAX_VALUE).occurrences, runSearch(model, 1).occurrences);
  }

  public void testChunkedSearchWalksTheSelectionRangesOfASearchInSelection() {
    configureFromText(TEXT);
    getEditor().getSelectionModel().setSelection(0, 11);
    FindModel model = findModel("ab");
    model.setGlobal(false); // search in selection - the search area then has ranges of its own
    List<TextRange> whole = runSearch(model, Integer.MAX_VALUE).occurrences;
    assertEquals(2, whole.size());
    assertEquals(whole, runSearch(model, 1).occurrences);
  }

  public void testASearchThatFitsInOneChunkIsPublishedInOneGo() {
    configureFromText(TEXT);
    Search search = runSearch("ab", Integer.MAX_VALUE);
    assertEquals(5, search.occurrences.size());
    assertEmpty(search.appended);
  }

  public void testEveryChunkAfterTheFirstReachesTheListenersAsAnAppend() {
    configureFromText(TEXT);
    Search search = runSearch("ab", 1);

    // The first chunk is reported as a full update so that listeners drop what the previous search left behind; every
    // chunk after it is reported as an append, in the order the chunks were found.
    assertEquals(5, search.occurrences.size());
    assertEquals(search.occurrences.subList(1, search.occurrences.size()), flatten(search.appended));
  }

  public void testMatchesBecomeVisibleWhileTheSearchIsStillRunning() {
    configureFromText(TEXT);
    Search search = runSearch("ab", 1);

    // Every notification sees more matches than the previous one, and the last one sees them all.
    assertEquals(List.of(1, 2, 3, 4, 5), search.countsWhileRunning);
    assertEquals(search.occurrences.size(), (int)search.countsWhileRunning.get(search.countsWhileRunning.size() - 1));
  }

  public void testTheCursorIsOnlySettledOnceEveryChunkIsIn() {
    configureFromText(TEXT);
    Search search = runSearch("ab", 1);

    // A cursor picked from a partial result set could not be the right one, so it stays unset until the search is over.
    assertEmpty(search.cursorsWhileRunning);
    assertNotNull(search.cursorWhenFinished);
  }

  /**
   * Anything that reacts to an occurrence reads the find model to interpret it, so publishing occurrences the model has
   * not caught up with hands out results nobody can act on: {@link SelectionManager} fails outright, and the find bar
   * drops the status update it was given. This is only observable on the first search of a {@link SearchResults}, which
   * is where the model starts out null.
   */
  public void testTheFindModelIsInPlaceBeforeTheFirstOccurrenceIsPublished() {
    configureFromText(TEXT);
    assertEmpty(runSearch("ab", 1).occurrencesSeenWithoutAFindModel);
  }

  /**
   * The failure the above prevents: a mouse press in the editor mid-search moves the cursor onto an occurrence that has
   * been published already, by way of {@link SearchResults#caretPositionChanged}. Driving that from a listener reaches
   * the same state a mouse press would find between two publications, and reaches it deterministically.
   */
  public void testMovingTheCaretOntoAPartialResultDoesNotFail() {
    configureFromText(TEXT);

    SearchResults searchResults = new SearchResults(getEditor(), getProject());
    try {
      searchResults.setChunkMatchLimit(1);
      searchResults.addListener(new SearchResults.SearchResultsListener() {
        @Override
        public void searchResultsUpdated(@NotNull SearchResults sr) {
          List<FindResult> occurrences = sr.getOccurrences();
          if (!sr.isUpdating() || occurrences.isEmpty()) return;
          // The most recently published occurrence, so that this is a move rather than a no-op: the caret starts out at
          // offset 0, which is where the first occurrence begins.
          getEditor().getCaretModel().moveToOffset(occurrences.get(occurrences.size() - 1).getStartOffset());
        }

        @Override
        public void cursorMoved() {}
      });

      searchResults.setUpdating(true);
      FindModel model = findModel("ab");
      assertTrue(searchResults.updateThreadSafe(model, false, null, searchResults.getStamp()).isDone());
      assertEquals(5, searchResults.getMatchesCount());
    }
    finally {
      searchResults.dispose();
    }
  }

  /**
   * The callback the search hands back is what {@link LivePreviewController#updateInBackground} retries from, and that
   * handler runs the whole update again, toolbar included. So a search that gives up has to say so on the EDT however
   * far from it the search itself ran - rejecting on the pooled thread the search runs on re-enters the update there
   * and trips the EDT assertion.
   * <p>
   * Both ways a search can give up - a chunk losing its read action to a write action, and the document changing
   * between two chunks - reject through the same helper, so covering either one covers both. This drives the first,
   * by failing the second chunk once the first has been published.
   */
  public void testASearchRunningOffTheEdtStillRejectsOnIt() {
    configureFromText(TEXT);

    SearchResults searchResults = new SearchResults(getEditor(), getProject());
    try {
      searchResults.setChunkMatchLimit(1);
      AtomicInteger chunks = new AtomicInteger();
      searchResults.setChunkHook(() -> {
        if (chunks.getAndIncrement() == 1) throw new ProcessCanceledException();
      });

      CompletableFuture<Boolean> rejectedOnEdt = new CompletableFuture<>();
      searchResults.setUpdating(true);
      AppExecutorUtil.getAppExecutorService().execute(() -> {
        searchResults.updateThreadSafe(findModel("ab"), false, null, searchResults.getStamp())
          .doWhenRejected(() -> rejectedOnEdt.complete(EventQueue.isDispatchThread()));
      });

      // Waiting has to keep the event queue running: the search publishes its chunks onto it, and so does the
      // rejection being asserted on.
      assertTrue("the search was rejected off the EDT", PlatformTestUtil.waitForFuture(rejectedOnEdt, 30_000));
    }
    finally {
      searchResults.setChunkHook(null);
      searchResults.dispose();
    }
  }

  /**
   * The time budget alone does not bound a chunk usefully: a plain-text scan finds far more matches inside 50ms than
   * are cheap to apply, and applying them is the half that costs - every occurrence becomes a range highlighter on the
   * EDT. So a production search caps how many occurrences one chunk may carry, which is what keeps each publication's
   * EDT event short. Unlike the tests above, this one lets the search pick its own chunk boundaries, which means
   * running it off the EDT: that is the only place a search is chunked at all.
   */
  public void testAProductionChunkIsBoundedByItsMatchCountAndNotOnlyByItsTimeBudget() {
    int matches = SearchResults.CHUNK_MATCH_LIMIT * 2 + 1; // enough to need three chunks, so one of them is a middle one
    configureFromText("ab ".repeat(matches));

    SearchResults searchResults = new SearchResults(getEditor(), getProject());
    try {
      Search search = new Search();
      searchResults.addListener(search);
      searchResults.setUpdating(true);

      CompletableFuture<Boolean> done = new CompletableFuture<>();
      AppExecutorUtil.getAppExecutorService().execute(() -> {
        searchResults.updateThreadSafe(findModel("ab"), false, null, searchResults.getStamp())
          .doWhenProcessed(() -> done.complete(!searchResults.isUpdating()));
      });
      // Waiting has to keep the event queue running: the search publishes its chunks onto it.
      assertTrue("the search did not complete", PlatformTestUtil.waitForFuture(done, 30_000));

      assertEquals(matches, searchResults.getMatchesCount());
      assertNotEmpty(search.appended); // the search really did take more than one chunk
      for (List<TextRange> chunk : search.appended) {
        assertTrue("a chunk carried " + chunk.size() + " occurrences", chunk.size() <= SearchResults.CHUNK_MATCH_LIMIT);
      }
      // The first chunk reaches the listeners as a full update rather than an append, so its size is the count the
      // first notification saw.
      int firstChunk = search.countsWhileRunning.getFirst();
      assertTrue("the first chunk carried " + firstChunk + " occurrences", firstChunk <= SearchResults.CHUNK_MATCH_LIMIT);
    }
    finally {
      searchResults.dispose();
    }
  }

  private void configureFromText(@NotNull String text) {
    configureFromFileText("file.txt", text);
  }

  private static @NotNull FindModel findModel(@NotNull String stringToFind) {
    FindModel model = new FindModel();
    model.setStringToFind(stringToFind);
    return model;
  }

  private @NotNull Search runSearch(@NotNull String stringToFind, int chunkMatchLimit) {
    return runSearch(findModel(stringToFind), chunkMatchLimit);
  }

  private @NotNull Search runSearch(@NotNull FindModel model, int chunkMatchLimit) {
    SearchResults searchResults = new SearchResults(getEditor(), getProject());
    try {
      searchResults.setChunkMatchLimit(chunkMatchLimit);
      Search search = new Search();
      searchResults.addListener(search);

      // What LivePreviewController does before handing the search off; it is also how a listener tells a partial result
      // set from a complete one.
      searchResults.setUpdating(true);
      ActionCallback callback = searchResults.updateThreadSafe(model, false, null, searchResults.getStamp());
      assertTrue("the search did not complete", callback.isDone());
      assertFalse("the search is still reported as running", searchResults.isUpdating());

      search.occurrences = ranges(searchResults.getOccurrences());
      search.cursorWhenFinished = searchResults.getCursor();
      return search;
    }
    finally {
      searchResults.dispose();
    }
  }

  private static @NotNull List<TextRange> ranges(@NotNull List<? extends TextRange> occurrences) {
    List<TextRange> ranges = new ArrayList<>(occurrences.size());
    for (TextRange occurrence : occurrences) {
      ranges.add(TextRange.create(occurrence));
    }
    return ranges;
  }

  private static @NotNull List<TextRange> flatten(@NotNull List<List<TextRange>> chunks) {
    List<TextRange> flattened = new ArrayList<>();
    for (List<TextRange> chunk : chunks) {
      flattened.addAll(chunk);
    }
    return flattened;
  }

  /** Records what a listener gets to see while the search is running, plus the state it settled on. */
  private static final class Search implements SearchResults.SearchResultsListener {
    private final List<List<TextRange>> appended = new ArrayList<>();
    private final List<Integer> countsWhileRunning = new ArrayList<>();
    private final List<FindResult> cursorsWhileRunning = new ArrayList<>();
    private final List<Integer> occurrencesSeenWithoutAFindModel = new ArrayList<>();

    private List<TextRange> occurrences;
    private FindResult cursorWhenFinished;

    @Override
    public void searchResultsUpdated(@NotNull SearchResults sr) {
      record(sr);
    }

    @Override
    public void searchResultsAppended(@NotNull SearchResults sr, @NotNull List<FindResult> added) {
      appended.add(ranges(added));
      record(sr);
    }

    @Override
    public void cursorMoved() {}

    private void record(@NotNull SearchResults sr) {
      if (!sr.isUpdating()) return; // the search has settled; this is no longer a partial result set
      countsWhileRunning.add(sr.getMatchesCount());
      if (sr.getCursor() != null) {
        cursorsWhileRunning.add(sr.getCursor());
      }
      if (sr.getFindModel() == null && sr.getMatchesCount() > 0) {
        occurrencesSeenWithoutAFindModel.add(sr.getMatchesCount());
      }
    }
  }
}
