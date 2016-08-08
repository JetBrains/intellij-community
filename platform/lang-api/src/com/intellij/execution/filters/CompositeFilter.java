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
package com.intellij.execution.filters;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class CompositeFilter implements Filter, FilterMixin {
  private static final Logger LOG = Logger.getInstance(CompositeFilter.class);

  private final List<Filter> myFilters = new ArrayList<>();
  private boolean myIsAnyHeavy;
  private boolean forceUseAllFilters;
  private final DumbService myDumbService;

  public CompositeFilter(@NotNull Project project) {
    myDumbService = DumbService.getInstance(project);
  }

  protected CompositeFilter(DumbService dumbService) {
    myDumbService = dumbService;
  }

  @Override
  @Nullable
  public Result applyFilter(final String line, final int entireLength) {
    final boolean dumb = myDumbService.isDumb();
    List<Filter> filters = myFilters;
    int count = filters.size();

    List<ResultItem> resultItems = null;
    for (int i = 0; i < count; i++) {
      Filter filter = filters.get(i);
      if (!dumb || DumbService.isDumbAware(filter)) {
        long t0 = System.currentTimeMillis();

        Result result;
        try {
          result = filter.applyFilter(line, entireLength);
        }
        catch (ProcessCanceledException ignore) {
          result = null;
        }
        catch (Throwable t) {
          throw new RuntimeException("Error while applying " + filter + " to '" + line + "'", t);
        }
        resultItems = merge(resultItems, result);

        t0 = System.currentTimeMillis() - t0;
        if (t0 > 1000) {
          LOG.warn(filter.getClass().getSimpleName() + ".applyFilter() took " + t0 + " ms on '''" + line + "'''");
        }
        if (shouldStopFiltering(result)) {
          break;
        }
      }
    }
    return createFinalResult(resultItems);
  }

  @Nullable
  private static Result createFinalResult(@Nullable List<ResultItem> resultItems) {
    if (resultItems == null) {
      return null;
    }
    if (resultItems.size() == 1) {
      ResultItem resultItem = resultItems.get(0);
      return new Result(resultItem.getHighlightStartOffset(), resultItem.getHighlightEndOffset(), resultItem.getHyperlinkInfo(),
                        resultItem.getHighlightAttributes(), resultItem.getFollowedHyperlinkAttributes());
    }
    return new Result(resultItems);
  }

  private boolean shouldStopFiltering(@Nullable Result result) {
    return result != null && result.getNextAction() == NextAction.EXIT && !forceUseAllFilters;
  }

  @Nullable
  protected List<ResultItem> merge(@Nullable List<ResultItem> resultItems, @Nullable Result newResult) {
    if (newResult != null) {
      if (resultItems == null) {
        resultItems = new ArrayList<>();
      }
      List<ResultItem> newItems = newResult.getResultItems();
      for (int i = 0; i < newItems.size(); i++) {
        ResultItem item = newItems.get(i);
        if (item.getHyperlinkInfo() == null || !intersects(resultItems, item)) {
          resultItems.add(item);
        }
      }
    }
    return resultItems;
  }

  protected boolean intersects(List<ResultItem> items, ResultItem newItem) {
    TextRange newItemTextRange = null;

    for (int i = 0; i < items.size(); i++) {
      ResultItem item = items.get(i);
      if (item.getHyperlinkInfo() != null) {
        if (newItemTextRange == null) {
          newItemTextRange = new TextRange(newItem.highlightStartOffset, newItem.highlightEndOffset);
        }
        if (newItemTextRange.intersectsStrict(item.highlightStartOffset, item.highlightEndOffset)) {
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
  public void applyHeavyFilter(@NotNull Document copiedFragment, int startOffset, int startLineNumber, @NotNull Consumer<AdditionalHighlight> consumer) {
    final boolean dumb = myDumbService.isDumb();
    List<Filter> filters = myFilters;
    int count = filters.size();

    for (int i = 0; i < count; i++) {
      Filter filter = filters.get(i);
      if (!(filter instanceof FilterMixin) || !((FilterMixin)filter).shouldRunHeavy()) continue;
      if (!dumb || DumbService.isDumbAware(filter)) {
        ((FilterMixin)filter).applyHeavyFilter(copiedFragment, startOffset, startLineNumber, consumer);
      }
    }
  }

  @NotNull
  @Override
  public String getUpdateMessage() {
    final boolean dumb = myDumbService.isDumb();
    List<Filter> filters = myFilters;
    final List<String> updateMessage = new ArrayList<>();
    int count = filters.size();

    for (int i = 0; i < count; i++) {
      Filter filter = filters.get(i);

      if (!(filter instanceof FilterMixin) || !((FilterMixin)filter).shouldRunHeavy()) continue;
      if (!dumb || DumbService.isDumbAware(filter)) {
        updateMessage.add(((FilterMixin)filter).getUpdateMessage());
      }
    }
    return updateMessage.size() == 1 ? updateMessage.get(0) : "Updating...";
  }

  public boolean isEmpty() {
    return myFilters.isEmpty();
  }

  public boolean isAnyHeavy() {
    return myIsAnyHeavy;
  }

  public void addFilter(final Filter filter) {
    myFilters.add(filter);
    myIsAnyHeavy |= filter instanceof FilterMixin;
  }

  public void setForceUseAllFilters(boolean forceUseAllFilters) {
    this.forceUseAllFilters = forceUseAllFilters;
  }

  @Override
  public String toString() {
    return "CompositeFilter: " + myFilters;
  }
}
