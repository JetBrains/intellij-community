/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author peter
 */
class AsyncFilterRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.FilterRunner");
  private static final ExecutorService ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("console filters", 1);
  private final EditorHyperlinkSupport myHyperlinks;
  private final Editor myEditor;
  private final Queue<HighlighterJob> myQueue = new ConcurrentLinkedQueue<>();
  @NotNull private List<FilterResult> myResults = new ArrayList<>();

  AsyncFilterRunner(EditorHyperlinkSupport hyperlinks, Editor editor) {
    myHyperlinks = hyperlinks;
    myEditor = editor;
  }

  void highlightHyperlinks(final Filter customFilter, final int startLine, final int endLine) {
    if (endLine < 0) return;

    myQueue.offer(new HighlighterJob(customFilter, startLine, endLine, myEditor.getDocument()));
    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
      runTasks();
      highlightAvailableResults();
    } else if (isQuick(ourExecutor.submit(this::runFiltersInBackground))) {
      highlightAvailableResults();
    }
  }

  private void runFiltersInBackground() {
    while (true) {
      boolean finished = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(this::runTasks);
      if (hasResults()) {
        ApplicationManager.getApplication().invokeLater(this::highlightAvailableResults, ModalityState.any());
      }
      if (finished) return;
      ProgressIndicatorUtils.yieldToPendingWriteActions();
    }
  }

  private static boolean isQuick(Future<?> future) {
    try {
      future.get(5, TimeUnit.MILLISECONDS);
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

  @SuppressWarnings("UnusedReturnValue")
  public boolean waitForPendingFilters(long timeoutMs) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    
    long started = System.currentTimeMillis();
    while (true) {
      if (myQueue.isEmpty()) {
        // results are available before queue is emptied, so process the last results, if any, and exit
        highlightAvailableResults();
        return true;
      }

      if (hasResults()) {
        highlightAvailableResults();
        continue;
      }

      if (System.currentTimeMillis() - started > timeoutMs) {
        return false;
      }
      TimeoutUtil.sleep(1);
    }
  }

  private void runTasks() {
    if (myEditor.isDisposed()) return;

    while (!myQueue.isEmpty()) {
      HighlighterJob highlighter = myQueue.peek();
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

  private interface FilterResult {
    void applyHighlights();
  }

  private class HighlighterJob {
    private final AtomicInteger startLine;
    private final int endLine;
    private final int initialMarkerOffset;
    private final RangeMarker endMarker;
    private final Filter filter;
    private final Document snapshot;

    HighlighterJob(Filter filter, int startLine, int endLine, Document document) {
      this.startLine = new AtomicInteger(startLine);
      this.endLine = endLine;
      this.filter = filter;

      initialMarkerOffset = document.getLineEndOffset(endLine);
      endMarker = document.createRangeMarker(initialMarkerOffset, initialMarkerOffset);
      snapshot = ((DocumentImpl)document).freeze();
    }

    boolean hasUnprocessedLines() {
      return !isOutdated() && startLine.get() <= endLine;
    }

    @Nullable
    AsyncFilterRunner.FilterResult analyzeNextLine() {
      int line = startLine.get();
      Filter.Result result = analyzeLine(line);
      LOG.assertTrue(line == startLine.getAndIncrement());
      return result == null ? null : () -> {
        if (!isOutdated()) {
          myHyperlinks.highlightHyperlinks(result, getOffsetDelta());
        }
      };
    }

    Filter.Result analyzeLine(int line) {
      int lineStart = snapshot.getLineStartOffset(line);
      if (lineStart + getOffsetDelta() < 0) return null;

      String lineText = EditorHyperlinkSupport.getLineText(snapshot, line, true);
      int endOffset = lineStart + lineText.length();
      return checkRange(filter, endOffset, filter.applyFilter(lineText, endOffset));
    }

    boolean isOutdated() {
      return !endMarker.isValid() || endMarker.getEndOffset() == 0;
    }

    int getOffsetDelta() {
      return endMarker.getStartOffset() - initialMarkerOffset;
    }

  }

}
