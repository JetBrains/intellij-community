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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author yole
 */
public abstract class ChooseByNameFilterConfiguration<T> implements PersistentStateComponent<ChooseByNameFilterConfiguration.Items>  {
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
  public void loadState(final Items state) {
    items = state;
  }

  /**
   * Set filtering state for file type
   *
   * @param type  a type of the file to update
   * @param value if false, a file type will be filtered out
   */
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
   */
  public boolean isFileTypeVisible(T type) {
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
    @Tag("file-type-list")
    @AbstractCollection(elementTag = "filtered-out-file-type", elementValueAttribute = "name", surroundWithTag = false)
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
