// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

class AsyncFilterRunner {
  private static final Logger LOG = Logger.getInstance(AsyncFilterRunner.class);
  private static final ExecutorService ourExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Console Filters");
  private final EditorHyperlinkSupport myHyperlinks;
  private final Editor myEditor;
  private final Queue<HighlighterJob> myQueue = new ConcurrentLinkedQueue<>();
  @NotNull private List<FilterResult> myResults = new ArrayList<>();

  AsyncFilterRunner(@NotNull EditorHyperlinkSupport hyperlinks, @NotNull Editor editor) {
    myHyperlinks = hyperlinks;
    myEditor = editor;
  }

  void highlightHyperlinks(@NotNull Project project,
                           @NotNull Filter customFilter,
                           int startLine,
                           int endLine) {
    if (endLine < 0) return;

    Document document = myEditor.getDocument();
    long startStamp = document.getModificationStamp();
    myQueue.offer(new HighlighterJob(project, customFilter, startLine, endLine, document));
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

  @NotNull
  private List<FilterResult> takeAvailableResults() {
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

  void waitForPendingFilters(long timeoutMs) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    
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
      TimeoutUtil.sleep(1);
    }
  }

  private void runTasks() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (myEditor.isDisposed()) return;

    while (!myQueue.isEmpty()) {
      HighlighterJob highlighter = myQueue.peek();
      if (!DumbService.isDumbAware(highlighter.filter) && DumbService.isDumb(highlighter.myProject)) return;
      while (highlighter.hasUnprocessedLines()) {
        ProgressManager.checkCanceled();
        addLineResult(highlighter.analyzeNextLine());
      }
      LOG.assertTrue(highlighter == myQueue.remove());
    }
  }

  private static Filter.Result checkRange(Filter filter, int endOffset, Filter.Result result) {
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
  private class FilterResult {
    private final DeltaTracker myDelta;
    private final Filter.Result myResult;

    FilterResult(DeltaTracker delta, Filter.Result result) {
      myDelta = delta;
      myResult = result;
    }

    void applyHighlights() {
      if (!myDelta.isOutdated()) {
        myHyperlinks.highlightHyperlinks(myResult, myDelta.getOffsetDelta());
      }
    }
  }

  private class HighlighterJob {
    @NotNull private final Project myProject;
    private final AtomicInteger startLine;
    private final int endLine;
    private final DeltaTracker delta;
    @NotNull
    private final Filter filter;
    @NotNull
    private final Document snapshot;

    HighlighterJob(@NotNull Project project,
                   @NotNull Filter filter,
                   int startLine,
                   int endLine,
                   @NotNull Document document) {
      myProject = project;
      this.startLine = new AtomicInteger(startLine);
      this.endLine = endLine;
      this.filter = filter;

      delta = new DeltaTracker(document, document.getLineEndOffset(endLine));

      snapshot = ((DocumentImpl)document).freeze();
    }

    boolean hasUnprocessedLines() {
      return !delta.isOutdated() && startLine.get() <= endLine;
    }

    @Nullable
    private AsyncFilterRunner.FilterResult analyzeNextLine() {
      int line = startLine.get();
      Filter.Result result = analyzeLine(line);
      LOG.assertTrue(line == startLine.getAndIncrement());
      return result == null ? null : new FilterResult(delta, result);
    }

    private Filter.Result analyzeLine(int line) {
      int lineStart = snapshot.getLineStartOffset(line);
      if (lineStart + delta.getOffsetDelta() < 0) return null;

      String lineText = EditorHyperlinkSupport.getLineText(snapshot, line, true);
      int endOffset = lineStart + lineText.length();
      return checkRange(filter, endOffset, filter.applyFilter(lineText, endOffset));
    }

  }

  private static class DeltaTracker {
    private final int initialMarkerOffset;
    private final RangeMarker endMarker;

    DeltaTracker(Document document, int offset) {
      initialMarkerOffset = offset;
      endMarker = document.createRangeMarker(initialMarkerOffset, initialMarkerOffset);
    }

    boolean isOutdated() {
      return !endMarker.isValid() || endMarker.getEndOffset() == 0;
    }

    int getOffsetDelta() {
      return endMarker.getStartOffset() - initialMarkerOffset;
    }

  }

}
