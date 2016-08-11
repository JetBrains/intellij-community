/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.util.gotoByName;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public abstract class FilteringGotoByModel<T> extends ContributorsBasedGotoByModel {
  /** current file types */
  private Set<T> myFilterItems;

  protected FilteringGotoByModel(@NotNull Project project, @NotNull ChooseByNameContributor[] contributors) {
    super(project, contributors);
  }

  /**
   * Set file types
   * @param filterItems a file types to set
   */
  public synchronized void setFilterItems(Collection<T> filterItems) {
    // get and set method are called from different threads
    myFilterItems = new HashSet<>(filterItems);
  }

  /**
   * @return get file types
   */
  @Nullable
  protected synchronized Collection<T> getFilterItems() {
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

  @Nullable
  protected abstract T filterValueFor(NavigationItem item);
}
