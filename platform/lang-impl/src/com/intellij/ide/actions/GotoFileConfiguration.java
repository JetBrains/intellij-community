/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.actions;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Configuration for file type filtering popup in "Go to | File" action.
 *
 * @author Constantine.Plotnikov
 */
@State(
    name = "GotoFileConfiguration",
    storages = {@Storage(
        id = "other",
        file = "$WORKSPACE_FILE$")})
public class GotoFileConfiguration implements PersistentStateComponent<GotoFileConfiguration.FileTypes> {
  /**
   * state object for the configuration
   */
  private FileTypes fileTypes = new FileTypes();

  /**
   * {@inheritDoc}
   */
  public FileTypes getState() {
    return fileTypes;
  }

  /**
   * {@inheritDoc}
   */
  public void loadState(final FileTypes state) {
    fileTypes = state;
  }

  /**
   * Set filtering state for file type
   *
   * @param type  a type of the file to duptate
   * @param value if false, a file type will be filtered out
   */
  public void setFileTypeVisible(FileType type, boolean value) {
    if (value) {
      fileTypes.getFilteredOutFileTypeNames().remove(type.getName());
    }
    else {
      fileTypes.getFilteredOutFileTypeNames().add(type.getName());
    }
  }

  /**
   * Check if file type should be filtered out
   *
   * @param type a file type to check
   * @return false if file of the sepecified type should be filtered out
   */
  public boolean isFileTypeVisible(FileType type) {
    return !fileTypes.getFilteredOutFileTypeNames().contains(type.getName());
  }

  /**
   * Get configuration instance
   *
   * @param project a project instance
   * @return a configuration instance
   */
  public static GotoFileConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, GotoFileConfiguration.class);
  }

  /**
   * A state for this configuraiton
   */
  public static class FileTypes {
    /**
     * a set of file types
     */
    private Set<String> filteredOutFileTypeNames = new LinkedHashSet<String>();

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
     * @param a new collection for file type names
     */
    public void setFilteredOutFileTypeNames(final Set<String> fileTypeNames) {
      this.filteredOutFileTypeNames = fileTypeNames;
    }
  }
}
