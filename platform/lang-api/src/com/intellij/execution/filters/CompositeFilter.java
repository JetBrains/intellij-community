/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CompositeFilter implements Filter, FilterMixin {
  private static final Logger LOG = Logger.getInstance(CompositeFilter.class);

  private final List<Filter> myFilters = new ArrayList<Filter>();
  private boolean myIsAnyHeavy;
  private boolean useAllFilters = true;
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
    //noinspection ForLoopReplaceableByForEach
    Result finalResult = null;
    for (int i = 0; i < count; i++) {
      Filter filter = filters.get(i);
      if (!dumb || DumbService.isDumbAware(filter)) {
        long t0 = System.currentTimeMillis();
        Result result = null;
        try {
          result = filter.applyFilter(line, entireLength);
        }
        catch (Throwable t) {
          throw new RuntimeException("Error while applying " + filter + " to '"+line+"'", t);
        }
        finalResult = merge(finalResult, result);
        t0 = System.currentTimeMillis() - t0;
        if (t0 > 1000) {
          LOG.warn(filter.getClass().getSimpleName() + ".applyFilter() took " + t0 + " ms on '''" + line + "'''");
        }
        if (finalResult != null && finalResult.getNextAction() == NextAction.EXIT && !useAllFilters) {
          return finalResult;
        }
      }
    }
    return finalResult;
  }

  protected Result merge(@Nullable Result finalResult, @Nullable Result result) {
    if (result != null) {
      if (finalResult == null) {
        finalResult = result;
      }
      else {
        final List<ResultItem> resultItems = mergeResultItems(finalResult.getResultItems(), result.getResultItems());
        finalResult.setResultItems(resultItems);
        finalResult.setNextAction(result.getNextAction());
      }
    }
    return finalResult;
  }

  private List<ResultItem> mergeResultItems(final List<ResultItem> finalResult, final List<ResultItem> result) {
    List<ResultItem> mergedResult;
    if (finalResult instanceof MyArrayList) {
      mergedResult = finalResult;
    }
    else {
      mergedResult = new MyArrayList<ResultItem>(finalResult.size() + result.size());
      mergedResult.addAll(finalResult);
    }

    for (ResultItem item : result) {
      if (item.hyperlinkInfo != null) {
        if (!intersects(mergedResult, item)) {
          mergedResult.add(item);
        }
      }
      else {
        mergedResult.add(item);
      }
    }
    return mergedResult;
  }

  protected boolean intersects(List<ResultItem> mergedList, ResultItem addedItem) {
    boolean intersects = false;
    TextRange addedItemTextRange = null;

    for (ResultItem item : mergedList) {
      if (item.hyperlinkInfo != null) {
        if (addedItemTextRange == null) {
          addedItemTextRange = new TextRange(addedItem.highlightStartOffset, addedItem.highlightEndOffset);
        }
        intersects = addedItemTextRange.intersectsStrict(item.highlightStartOffset, item.highlightEndOffset);
        if (intersects) {
          break;
        }
      }
    }
    return intersects;
  }

  @Override
  public boolean shouldRunHeavy() {
    for (Filter filter : myFilters) {
      if (filter instanceof FilterMixin && ((FilterMixin)filter).shouldRunHeavy()) return true;
    }
    return false;
  }

  @Override
  public void applyHeavyFilter(Document copiedFragment, int startOffset, int startLineNumber, Consumer<AdditionalHighlight> consumer) {
    final boolean dumb = myDumbService.isDumb();
    List<Filter> filters = myFilters;
    int count = filters.size();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < count; i++) {
      Filter filter = filters.get(i);
      if (! (filter instanceof FilterMixin) || !((FilterMixin)filter).shouldRunHeavy()) continue;
      if (!dumb || DumbService.isDumbAware(filter)) {
        ((FilterMixin) filter).applyHeavyFilter(copiedFragment, startOffset, startLineNumber, consumer);
      }
    }
  }

  @Override
  public String getUpdateMessage() {
    final boolean dumb = myDumbService.isDumb();
    List<Filter> filters = myFilters;
    final List<String> updateMessage = new ArrayList<String>();
    int count = filters.size();
    //noinspection ForLoopReplaceableByForEach
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

  public void setUseAllFilters(boolean useAllFilters) {
    this.useAllFilters = useAllFilters;
  }

  /**
   * for memory allocation reduction without danger of changing clients arraylist
   */
  private static class MyArrayList<T> extends ArrayList<T> {
    MyArrayList(int initialCapacity) {
      super(initialCapacity);
    }
  }
}
