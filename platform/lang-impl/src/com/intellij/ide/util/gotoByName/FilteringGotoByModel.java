// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class FilteringGotoByModel<T> extends ContributorsBasedGotoByModel {
  /** current file types */
  private Set<T> myFilterItems;

  protected FilteringGotoByModel(@NotNull Project project, ChooseByNameContributor @NotNull [] contributors) {
    super(project, contributors);
  }

  protected FilteringGotoByModel(@NotNull Project project, @NotNull List<ChooseByNameContributor> contributors) {
    super(project, contributors);
  }

  /**
   * Set file types
   * @param filterItems a file types to set
   */
  public synchronized void setFilterItems(Collection<? extends T> filterItems) {
    // get and set method are called from different threads
    myFilterItems = new HashSet<>(filterItems);
  }

  /**
   * @return get file types
   */
  protected synchronized @Nullable Collection<T> getFilterItems() {
    // get and set method are called from different threads
    return myFilterItems;
  }

  @Override
  protected boolean acceptItem(final NavigationItem item) {
    T filterValue = filterValueFor(item);
    if (filterValue != null) {
      final Collection<T> types = getFilterItems();
      return types == null || types.contains(filterValue);
    }
    return true;
  }

  protected abstract @Nullable T filterValueFor(NavigationItem item);
}
