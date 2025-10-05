// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Expirable;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.Promise;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

final class AsyncFilterRunner {
  private static final Logger LOG = Logger.getInstance(AsyncFilterRunner.class);
  private static final ExecutorService ourExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Console Filters");
  private final EditorHyperlinkSupport myHyperlinks;
  private final Editor myEditor;
  private final Queue<HighlighterJob> myQueue = new ConcurrentLinkedQueue<>();
  private @NotNull List<FilterResult> myResults = new ArrayList<>();

  /**
   * If true, deletions from the document top are tracked manually, not via `RangeMarker`.
   */
  private final boolean myTrackDocumentChangesManually;

  AsyncFilterRunner(@NotNull EditorHyperlinkSupport hyperlinks, @NotNull Editor editor, boolean trackDocumentChangesManually) {
    myHyperlinks = hyperlinks;
    myEditor = editor;
    myTrackDocumentChangesManually = trackDocumentChangesManually;
    if (trackDocumentChangesManually) {
      trackDocumentChanges(editor.getDocument());
    }
  }

  private void trackDocumentChanges(@NotNull Document document) {
    document.addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        if (event.getOffset() == 0 && event.getNewLength() == 0) {
          if (event.getOldLength() > 0) {
            for (DeltaTracker deltaTracker : collectActiveDeltaTrackers()) {
              deltaTracker.onDeletedFromDocumentTop(event.getOldLength());
            }
          }
        }
        else {
          for (DeltaTracker deltaTracker : collectActiveDeltaTrackers()) {
            deltaTracker.stopAt(event.getOffset());
          }
        }
      }
    });
  }

  private @NotNull Set<DeltaTracker> collectActiveDeltaTrackers() {
    List<DeltaTracker> pendingResultTrackers;
    synchronized (myQueue) {
      pendingResultTrackers = ContainerUtil.map(myResults, result -> result.myDelta);
    }
    Set<DeltaTracker> trackers = new HashSet<>(pendingResultTrackers);
    for (HighlighterJob runningJob : myQueue) {
      trackers.add(runningJob.delta);
    }
    return trackers;
  }

  void highlightHyperlinks(@NotNull Project project,
                           @NotNull Filter customFilter,
                           int startLine,
                           int endLine,
                           @NotNull Expirable token) {
    if (endLine < 0) return;

    Document document = myEditor.getDocument();
    long startStamp = document.getModificationStamp();
    if (myTrackDocumentChangesManually) {
      for (DeltaTracker deltaTracker : collectActiveDeltaTrackers()) {
        deltaTracker.stopAt(document.getLineStartOffset(startLine));
      }
    }

    myQueue.offer(new HighlighterJob(project, customFilter, startLine, endLine, document, token));
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      runTasks();
      highlightAvailableResults();
      return;
    }

    Promise<?> promise = ReadAction.nonBlocking(this::runTasks).expireWhen(() -> document.getModificationStamp() != startStamp).submit(ourExecutor);

    if (isQuick(promise)) {
      highlightAvailableResults();
    }
    else {
      promise.onSuccess(__ -> {
        if (hasResults()) {
          ApplicationManager.getApplication().invokeLater(this::highlightAvailableResults, ModalityState.any());
        }
      });
    }
  }

  private static boolean isQuick(Promise<?> future) {
    try {
      future.blockingGet(5, TimeUnit.MILLISECONDS);
      return true;
    }
    catch (TimeoutException ignored) {
      return false;
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void highlightAvailableResults() {
    for (FilterResult result : takeAvailableResults()) {
      result.applyHighlights();
    }
  }

  private boolean hasResults() {
    synchronized (myQueue) {
      return !myResults.isEmpty();
    }
  }

  private @NotNull List<FilterResult> takeAvailableResults() {
    synchronized (myQueue) {
      List<FilterResult> results = myResults;
      myResults = new ArrayList<>();
      return results;
    }
  }

  private void addLineResult(@Nullable FilterResult result) {
    if (result == null) return;

    synchronized (myQueue) {
      myResults.add(result);
    }
  }

  @TestOnly
  void waitForPendingFilters(long timeoutMs) {
    ThreadingAssertions.assertEventDispatchThread();

    long started = System.currentTimeMillis();
    while (true) {
      if (myQueue.isEmpty()) {
        // results are available before queue is emptied, so process the last results, if any, and exit
        highlightAvailableResults();
        return;
      }

      if (hasResults()) {
        highlightAvailableResults();
        continue;
      }

      if (System.currentTimeMillis() - started > timeoutMs) {
        return;
      }
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        UIUtil.dispatchAllInvocationEvents();
      }
      TimeoutUtil.sleep(1);
    }
  }

  private void runTasks() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (myEditor.isDisposed()) return;

    while (!myQueue.isEmpty()) {
      HighlighterJob highlighter = myQueue.peek();
      if (!DumbService.getInstance(highlighter.myProject).isUsableInCurrentContext(highlighter.filter)) return;
      while (highlighter.hasUnprocessedLines()) {
        ProgressManager.checkCanceled();
        addLineResult(highlighter.analyzeNextLine());
      }
      LOG.assertTrue(highlighter == myQueue.remove());
    }
  }

  static Filter.Result checkRange(Filter filter, int endOffset, Filter.Result result) {
    if (result != null) {
      for (Filter.ResultItem resultItem : result.getResultItems()) {
        int start = resultItem.getHighlightStartOffset();
        int end = resultItem.getHighlightEndOffset();
        if (end < start || end > endOffset) {
          LOG.error("Filter returned wrong range: start=" + start + "; end=" + end + "; max=" + endOffset + "; filter=" + filter);
        }
      }
    }
    return result;
  }

  /**
   * It's important that FilterResult doesn't reference frozen document from {@link HighlighterJob#snapshot},
   * as the lifetime of FilterResult is longer (until EDT is free to apply events), and there can be many jobs
   * holding many document snapshots all together consuming a lot of memory.
   */
  private final class FilterResult {
    private final DeltaTracker myDelta;
    private final Filter.Result myResult;

    FilterResult(DeltaTracker delta, Filter.Result result) {
      myDelta = delta;
      myResult = result;
    }

    void applyHighlights() {
      if (!myDelta.isOutdated()) {
        myHyperlinks.highlightHyperlinks(myResult, item -> {
          int startOffset = item.getHighlightStartOffset();
          int endOffset = item.getHighlightEndOffset();
          if (myDelta.isSnapshotRangeValid(startOffset, endOffset)) {
            int offsetDelta = myDelta.getOffsetDelta();
            return new TextRange(startOffset + offsetDelta, endOffset + offsetDelta);
          }
          return null;
        });
      }
    }
  }

  private final class HighlighterJob {
    private final @NotNull Project myProject;
    private final AtomicInteger startLine;
    private final int endLine;
    private final DeltaTracker delta;
    private final @NotNull Filter filter;
    private final @NotNull Document snapshot;

    HighlighterJob(@NotNull Project project,
                   @NotNull Filter filter,
                   int startLine,
                   int endLine,
                   @NotNull Document document,
                   @NotNull Expirable expirableToken) {
      myProject = project;
      this.startLine = new AtomicInteger(startLine);
      this.endLine = endLine;
      this.filter = filter;

      delta = new DeltaTracker(AsyncFilterRunner.this, document, document.getLineEndOffset(endLine), expirableToken);

      snapshot = ((DocumentImpl)document).freeze();
    }

    boolean hasUnprocessedLines() {
      return !delta.isOutdated() && startLine.get() <= endLine;
    }

    private @Nullable AsyncFilterRunner.FilterResult analyzeNextLine() {
      int line = startLine.get();
      Filter.Result result = analyzeLine(line);
      LOG.assertTrue(line == startLine.getAndIncrement());
      return result == null ? null : new FilterResult(delta, result);
    }

    private @Nullable Filter.Result analyzeLine(int line) {
      int lineStartOffset = snapshot.getLineStartOffset(line);
      int lineEndOffset = snapshot.getLineEndOffset(line);
      if (!delta.isSnapshotRangeValid(lineStartOffset, lineEndOffset)) {
        return null;
      }

      String lineText = EditorHyperlinkSupport.getLineText(snapshot, line, true);
      int endOffset = lineStartOffset + lineText.length();
      return checkRange(filter, endOffset, filter.applyFilter(lineText, endOffset));
    }

  }

  private static final class DeltaTracker {
    private final AsyncFilterRunner myRunner;
    private final int initialMarkerOffset;
    private final RangeMarker endMarker;
    private final @NotNull Expirable myExpirableToken;

    /** These fields can be accessed only if {@link #myTrackDocumentChangesManually} is true */
    private final AtomicInteger myDeletedLengthFromDocumentTop = new AtomicInteger(0);
    private final AtomicInteger myStopOffset;

    DeltaTracker(@NotNull AsyncFilterRunner runner, @NotNull Document document, int offset, @NotNull Expirable token) {
      myRunner = runner;
      myExpirableToken = token;
      initialMarkerOffset = offset;
      endMarker = document.createRangeMarker(initialMarkerOffset, initialMarkerOffset);
      myStopOffset = new AtomicInteger(offset);
    }

    boolean isOutdated() {
      return !endMarker.isValid() || endMarker.getEndOffset() == 0 || myExpirableToken.isExpired();
    }

    int getOffsetDelta() {
      if (myRunner.myTrackDocumentChangesManually) {
        return -myDeletedLengthFromDocumentTop.get();
      }
      return endMarker.getStartOffset() - initialMarkerOffset;
    }

    void onDeletedFromDocumentTop(int deletedLengthFromDocumentTop) {
      myDeletedLengthFromDocumentTop.addAndGet(deletedLengthFromDocumentTop);
    }

    void stopAt(int offset) {
      int snapshotOffset = offset + myDeletedLengthFromDocumentTop.get();
      myStopOffset.set(Math.min(myStopOffset.get(), snapshotOffset));
    }

    boolean isSnapshotRangeValid(int startOffset, int endOffset) {
      if (startOffset + getOffsetDelta() < 0) {
        // the top of the document has been deleted, including this line
        return false;
      }
      if (myRunner.myTrackDocumentChangesManually && endOffset > myStopOffset.get()) {
        return false;
      }
      return true;
    }
  }

}
