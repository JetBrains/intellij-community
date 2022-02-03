/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


/**
 * <p>FiltersHandler represents a {@link RowFilter} instance that
 * can be attached to a {@link JTable} to compose dynamically the
 * outcome of one or more filter editors. As such, it is a dynamic filter, which
 * updates the table when there are changes in any of the composed sub
 * filters.</p>
 *
 * <p>Users have, after version 3.2, no direct use for this class</p>
 *
 * <p>In Java 6, a filter is automatically associated to a {@link
 * RowSorter}, so {@link JTable} instances with a
 * TableFilter must define their own {@link RowSorter}. Being this
 * not the case, the TableFilter will automatically set the default {@link
 * RowSorter} in that table. That is, tables with a TableFilter will
 * always have sorting enabled.</p>
 *
 * <p>The {@link RowSorter} interface does not support filtering
 * capabilities, which are only enabled via the {@link
 * DefaultRowSorter} class. If the registered table uses any sorter
 * that does not subclass the {@link DefaultRowSorter} class, the
 * TableFilter will perform <b>no filtering at all</b>.</p>
 */
public class FiltersHandler extends AbstractFiltersHandler {
  private RowFilter<?, ?> currentFilter;
  private ChoicesHandler choicesHandler;
  private boolean filterOnUpdates = FilterSettings.filterOnUpdates;

  FiltersHandler(@Nullable AutoChoices autoChoices,
                 @NotNull IParserModel parserModel) {
    setAdaptiveChoices(FilterSettings.adaptiveChoices);
    setAutoChoices(autoChoices);
    setParserModel(parserModel);
  }

  @Nullable
  @Override
  protected RowFilter<?, ?> getCurrentFilter() {
    return currentFilter;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void setCurrentFilter(@Nullable RowFilter<?, ?> filter) {
    currentFilter = filter;
    RowSorter<?> sorter = getSorter();
    if (sorter != null) {
      ((DefaultRowSorter)sorter).setRowFilter(filter);
    }
  }

  @NotNull
  @Override
  protected ChoicesHandler getChoicesHandler() {
    return choicesHandler;
  }

  @Override
  public boolean isFilterOnUpdates() {
    return filterOnUpdates;
  }

  @Override
  public void setFilterOnUpdates(boolean enable) {
    filterOnUpdates = enable;
    if (getSorter() != null) {
      ((DefaultRowSorter<?, ?>)getSorter()).setSortsOnUpdates(enable);
    }
  }

  @Override
  public boolean isAdaptiveChoices() {
    return choicesHandler instanceof AdaptiveChoicesHandler;
  }

  @Override
  public void setAdaptiveChoices(boolean enableAdaptiveChoices) {
    boolean reenable = false;
    if (choicesHandler != null) {
      if (enableAdaptiveChoices == isAdaptiveChoices()) {
        return;
      }
      enableNotifications(false);
      if (choicesHandler != null) {
        choicesHandler.setInterrupted(true);
      }
      reenable = true;
    }
    if (enableAdaptiveChoices) {
      choicesHandler = new AdaptiveChoicesHandler(this);
    } else {
      choicesHandler = new NonAdaptiveChoicesHandler(this);
    }
    if (reenable) {
      enableNotifications(true);
    }
  }
}
