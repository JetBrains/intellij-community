// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.TypeVisibilityStateHolder;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;


public abstract class ChooseByNameFilterConfiguration<T>
  implements TypeVisibilityStateHolder<T>, PersistentStateComponent<ChooseByNameFilterConfiguration.Items> {
  /**
   * state object for the configuration
   */
  private Items items = new Items();

  /**
   * {@inheritDoc}
   */
  @Override
  public Items getState() {
    return items;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void loadState(@NotNull Items state) {
    items = state;
  }

  @Override
  public void setVisible(T type, boolean value) {
    if (value) {
      items.getFilteredOutFileTypeNames().remove(nameForElement(type));
    }
    else {
      items.getFilteredOutFileTypeNames().add(nameForElement(type));
    }
  }

  protected abstract String nameForElement(T type);

  /**
   * Check if file type should be filtered out
   *
   * @param type a file type to check
   * @return false if file of the specified type should be filtered out
   * @deprecated use a more general method {@link #isVisible}
   */
  @Deprecated
  public boolean isFileTypeVisible(T type) {
    return isVisible(type);
  }

  @Override
  public boolean isVisible(T type) {
    return !items.getFilteredOutFileTypeNames().contains(nameForElement(type));
  }

  /**
   * A state for this configuration
   */
  public static class Items {
    /**
     * a set of file types
     */
    private Set<String> filteredOutFileTypeNames = new LinkedHashSet<>();

    /**
     * @return names for file types
     */
    @XCollection(propertyElementName = "file-type-list", elementName = "filtered-out-file-type", valueAttributeName = "name")
    public Set<String> getFilteredOutFileTypeNames() {
      return filteredOutFileTypeNames;
    }

    /**
     * Set file type names
     *
     * @param fileTypeNames a new collection for file type names
     */
    public void setFilteredOutFileTypeNames(final Set<String> fileTypeNames) {
      this.filteredOutFileTypeNames = fileTypeNames;
    }
  }
}
