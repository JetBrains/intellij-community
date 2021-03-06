// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ide.actions.bigPopup.ShowFilterAction;
import com.intellij.ide.util.ElementsChooser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SearchEverywhereFiltersAction<T> extends ShowFilterAction {
  final PersistentSearchEverywhereContributorFilter<T> filter;
  final Runnable rebuildRunnable;
  final ElementsChooser.StatisticsCollector<T> myStatisticsCollector;

  public SearchEverywhereFiltersAction(@NotNull PersistentSearchEverywhereContributorFilter<T> filter,
                                       @NotNull Runnable rebuildRunnable) {
    this(filter, rebuildRunnable, null);
  }

  SearchEverywhereFiltersAction(@NotNull PersistentSearchEverywhereContributorFilter<T> filter,
                                @NotNull Runnable rebuildRunnable,
                                @Nullable ElementsChooser.StatisticsCollector<T> collector) {
    this.filter = filter;
    this.rebuildRunnable = rebuildRunnable;
    myStatisticsCollector = collector;
  }

  public PersistentSearchEverywhereContributorFilter<T> getFilter() {
    return filter;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  protected boolean isActive() {
    return filter.getAllElements().size() != filter.getSelectedElements().size();
  }

  @Override
  protected ElementsChooser<?> createChooser() {
    return createChooser(filter, rebuildRunnable, myStatisticsCollector);
  }

  private static <T> ElementsChooser<T> createChooser(@NotNull PersistentSearchEverywhereContributorFilter<T> filter,
                                                      @NotNull Runnable rebuildRunnable,
                                                      @Nullable ElementsChooser.StatisticsCollector<T> statisticsCollector) {
    ElementsChooser<T> res = new ElementsChooser<>(filter.getAllElements(), false) {
      @Override
      protected String getItemText(@NotNull T value) {
        return filter.getElementText(value);
      }

      @Nullable
      @Override
      protected Icon getItemIcon(@NotNull T value) {
        return filter.getElementIcon(value);
      }
    };
    res.markElements(filter.getSelectedElements());
    ElementsChooser.ElementsMarkListener<T> listener = (element, isMarked) -> {
      filter.setSelected(element, isMarked);
      rebuildRunnable.run();
    };
    res.addElementsMarkListener(listener);

    if (statisticsCollector != null) {
      res.addStatisticsCollector(statisticsCollector);
    }

    return res;
  }
}
