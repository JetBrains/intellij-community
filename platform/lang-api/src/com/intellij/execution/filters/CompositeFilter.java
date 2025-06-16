// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.diagnostic.PluginException;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompositeFilter implements Filter, FilterMixin, DumbAware {
  private static final Logger LOG = Logger.getInstance(CompositeFilter.class);

  private final List<Filter> myFilters;
  private boolean myIsAnyHeavy;
  private boolean forceUseAllFilters;
  private final DumbService myDumbService;

  public CompositeFilter(@NotNull Project project) {
    this(project, Collections.emptyList());
  }

  public CompositeFilter(@NotNull Project project, @NotNull List<? extends Filter> filters) {
    myDumbService = DumbService.getInstance(project);
    myFilters = new ArrayList<>(filters);
    myIsAnyHeavy = ContainerUtil.exists(filters, filter -> filter instanceof FilterMixin);
  }

  @TestOnly
  @ApiStatus.Internal
  public CompositeFilter(@NotNull DumbService dumbService) {
    myDumbService = dumbService;
    myFilters = new ArrayList<>();
  }

  @Override
  public @Nullable Result applyFilter(@NotNull String line, int entireLength) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    boolean dumb = myDumbService.isDumb();
    List<Filter> filters = myFilters;
    int count = filters.size();

    List<ResultItem> resultItems = null;
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < count; i++) {
      ProgressManager.checkCanceled();
      Filter filter = filters.get(i);
      if (myDumbService.isUsableInCurrentContext(filter)) {
        long t0 = System.currentTimeMillis();

        Result result;
        try {
          result = filter.applyFilter(line, entireLength);
        }
        catch (ProcessCanceledException ignore) {
          result = null;
        }
        catch (Throwable t) {
          throw new ApplyFilterException("Error while applying " + filter + " to '" + line + "'", t);
        }
        if (result != null) {
          resultItems = merge(resultItems, result, entireLength, filter);
        }

        t0 = System.currentTimeMillis() - t0;
        if (t0 > 1000) {
          LOG.warn(filter.getClass().getSimpleName() + ".applyFilter() took " + t0 + " ms on '''" + line + "'''");
        }
        if (result != null && shouldStopFiltering(result)) {
          break;
        }
      }
    }
    if (resultItems == null) {
      return null;
    }
    return createFinalResult(resultItems);
  }

  private static @NotNull Result createFinalResult(@NotNull List<? extends ResultItem> resultItems) {
    if (resultItems.size() == 1) {
      ResultItem resultItem = resultItems.get(0);
      return new Result(resultItem.getHighlightStartOffset(), resultItem.getHighlightEndOffset(), resultItem.getHyperlinkInfo(),
                        resultItem.getHighlightAttributes(), resultItem.getFollowedHyperlinkAttributes()) {
        @Override
        public int getHighlighterLayer() {
          return resultItem.getHighlighterLayer();
        }
      };
    }
    return new Result(resultItems);
  }

  private boolean shouldStopFiltering(@NotNull Result result) {
    return result.getNextAction() == NextAction.EXIT && !forceUseAllFilters;
  }

  private static @NotNull List<ResultItem> merge(@Nullable List<ResultItem> resultItems, @NotNull Result newResult, int entireLength, @NotNull Filter filter) {
    List<ResultItem> newItems = newResult.getResultItems();
    if (resultItems == null) {
      resultItems = new ArrayList<>(newItems.size());
    }
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < newItems.size(); i++) {
      ResultItem item = newItems.get(i);
      if ((item.getHyperlinkInfo() == null || !intersects(resultItems, item)) && checkOffsetsCorrect(item, entireLength, filter)) {
        resultItems.add(item);
      }
    }
    return resultItems;
  }

  private static boolean checkOffsetsCorrect(@NotNull ResultItem item, int entireLength, @NotNull Filter filter) {
    int start = item.getHighlightStartOffset();
    int end = item.getHighlightEndOffset();
    if (end < start || end > entireLength) {
      String message = "Filter returned wrong range: start=" + start + "; end=" + end + "; length=" + entireLength + "; filter=" + filter;
      PluginException.logPluginError(LOG, message, null, filter.getClass());
      return false;
    }
    return true;
  }

  @ApiStatus.Internal
  protected static boolean intersects(@NotNull List<? extends ResultItem> items, @NotNull ResultItem newItem) {
    TextRange newItemTextRange = null;

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < items.size(); i++) {
      ResultItem item = items.get(i);
      if (item.getHyperlinkInfo() != null) {
        if (newItemTextRange == null) {
          newItemTextRange = new TextRange(newItem.getHighlightStartOffset(), newItem.getHighlightEndOffset());
        }
        if (newItemTextRange.intersectsStrict(item.getHighlightStartOffset(), item.getHighlightEndOffset())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean shouldRunHeavy() {
    for (Filter filter : myFilters) {
      if (filter instanceof FilterMixin && ((FilterMixin)filter).shouldRunHeavy()) return true;
    }
    return false;
  }

  @Override
  public void applyHeavyFilter(@NotNull Document copiedFragment, int startOffset, int startLineNumber, @NotNull Consumer<? super AdditionalHighlight> consumer) {
    List<Filter> filters = myFilters;
    int count = filters.size();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < count; i++) {
      Filter filter = filters.get(i);
      if (filter instanceof FilterMixin && ((FilterMixin)filter).shouldRunHeavy()) {
        ((FilterMixin)filter).applyHeavyFilter(copiedFragment, startOffset, startLineNumber, consumer);
      }
    }
  }

  @Override
  public @NotNull @Nls String getUpdateMessage() {
    List<Filter> filters = myFilters;
    List<String> updateMessage = new ArrayList<>();
    int count = filters.size();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < count; i++) {
      Filter filter = filters.get(i);

      if (filter instanceof FilterMixin && ((FilterMixin)filter).shouldRunHeavy()) {
        updateMessage.add(((FilterMixin)filter).getUpdateMessage());
      }
    }
    return updateMessage.size() == 1 ? updateMessage.get(0) : LangBundle.message("updating.filters");
  }

  public boolean isEmpty() {
    return myFilters.isEmpty();
  }

  public boolean isAnyHeavy() {
    return myIsAnyHeavy;
  }

  public void addFilter(@NotNull Filter filter) {
    myFilters.add(filter);
    myIsAnyHeavy |= filter instanceof FilterMixin;
  }

  public @NotNull List<Filter> getFilters() {
    return Collections.unmodifiableList(myFilters);
  }

  public void setForceUseAllFilters(boolean forceUseAllFilters) {
    this.forceUseAllFilters = forceUseAllFilters;
  }

  @Override
  public String toString() {
    return "CompositeFilter: " + myFilters;
  }

  public static class ApplyFilterException extends RuntimeException {
    private ApplyFilterException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
