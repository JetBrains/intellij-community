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

/**
 * @author peter
 */
class AsyncFilterRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.impl.FilterRunner");
  private static final ExecutorService ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("console filters", 1);
  private final EditorHyperlinkSupport myHyperlinks;
  private final Editor myEditor;
  private final Queue<LineHighlighter> myQueue = new ConcurrentLinkedQueue<>();
  private final List<FilterResult> myResults = new ArrayList<>();

  AsyncFilterRunner(EditorHyperlinkSupport hyperlinks, Editor editor) {
    myHyperlinks = hyperlinks;
    myEditor = editor;
  }

  void highlightHyperlinks(final Filter customFilter, final int startLine, final int endLine) {
    if (endLine < 0) return;

    queueTasks(customFilter, startLine, endLine);
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
    synchronized (myResults) {
      return !myResults.isEmpty();
    }
  }

  @NotNull
  private List<FilterResult> takeAvailableResults() {
    synchronized (myResults) {
      List<FilterResult> results = new ArrayList<>(myResults);
      myResults.clear();
      return results;
    }
  }

  private void addLineResult(@Nullable FilterResult result) {
    if (result == null) return;

    synchronized (myResults) {
      myResults.add(result);
    }
  }

  @SuppressWarnings("UnusedReturnValue")
  public boolean waitForPendingFilters(long timeoutMs) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    
    long started = System.currentTimeMillis();
    while (!myQueue.isEmpty()) {
      if (hasResults()) {
        highlightAvailableResults();
      } else {
        TimeoutUtil.sleep(1);
      }

      timeoutMs -= System.currentTimeMillis() - started;
      if (timeoutMs < 1) return false;
    }
    
    return true;
  }

  private void queueTasks(Filter filter, int startLine, int endLine) {
    Document document = myEditor.getDocument();
    int markerOffset = document.getLineEndOffset(endLine);
    RangeMarker marker = document.createRangeMarker(markerOffset, markerOffset);
    for (int line = startLine; line <= endLine; line++) {
      myQueue.offer(processLine(document, filter, line, markerOffset, marker));
    }
  }

  @NotNull
  private LineHighlighter processLine(Document document, Filter filter, int line, int initialMarkerOffset, RangeMarker marker) {
    int lineEnd = document.getLineEndOffset(line);
    int endOffset = lineEnd + (lineEnd < document.getTextLength() ? 1 /* for \n */ : 0);
    CharSequence text = EditorHyperlinkSupport.getLineSequence(document, line, true);
    return () -> runFilterForLine(initialMarkerOffset, marker, filter, endOffset, text);
  }

  @Nullable
  private FilterResult runFilterForLine(int initialMarkerOffset, RangeMarker marker, Filter filter, int endOffset, CharSequence lineText) {
    if (!marker.isValid() || marker.getEndOffset() == 0) return null;

    Filter.Result result = checkRange(filter, endOffset, filter.applyFilter(lineText.toString(), endOffset));
    return result == null ? null : () -> {
      if (marker.isValid()) {
        myHyperlinks.highlightHyperlinks(result, marker.getStartOffset() - initialMarkerOffset);
      }
    };
  }

  private void runTasks() {
    if (myEditor.isDisposed()) return;

    while (!myQueue.isEmpty()) {
      ProgressManager.checkCanceled();
      LineHighlighter highlighter = myQueue.peek();
      addLineResult(highlighter.runFilterForLine());
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

  private interface LineHighlighter {
    @Nullable FilterResult runFilterForLine();
  }

  private interface FilterResult {
    void applyHighlights();
  }

}
