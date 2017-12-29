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
package com.intellij.ide.util.importProject;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class ModulesLayoutPanel extends ProjectLayoutPanel<ModuleDescriptor>{
  private final LibraryFilter myLibrariesFilter;

  public interface LibraryFilter {
    boolean isLibraryChosen(LibraryDescriptor libDescriptor);
  }
  public ModulesLayoutPanel(ModuleInsight insight, final LibraryFilter libFilter) {
    super(insight);
    myLibrariesFilter = libFilter;
  }

  protected String getElementName(final ModuleDescriptor entry) {
    return entry.getName();
  }

  protected void setElementName(final ModuleDescriptor entry, final String name) {
    entry.setName(name);
  }

  protected List<ModuleDescriptor> getEntries() {
    final List<ModuleDescriptor> modules = getInsight().getSuggestedModules();
    return modules != null? modules : Collections.emptyList();
  }

  protected Collection getDependencies(final ModuleDescriptor entry) {
    final List<Object> deps = new ArrayList<>(entry.getDependencies());
    final Collection<LibraryDescriptor> libDependencies = getInsight().getLibraryDependencies(entry);
    for (LibraryDescriptor libDependency : libDependencies) {
      if (myLibrariesFilter.isLibraryChosen(libDependency)) {
        deps.add(libDependency);
      }
    }
    return deps;
  }

  @Nullable
  protected ModuleDescriptor merge(final List<ModuleDescriptor> entries) {
    final ModuleInsight insight = getInsight();
    ModuleDescriptor mainDescr = null;
    for (ModuleDescriptor entry : entries) {
      if (mainDescr == null) {
        mainDescr = entry;
      }
      else {
        insight.merge(mainDescr, entry);
      }
    }
    return mainDescr;
  }

  protected ModuleDescriptor split(final ModuleDescriptor entry, final String newEntryName, final Collection<File> extractedData) {
    return getInsight().splitModule(entry, newEntryName, extractedData);
  }

  protected Collection<File> getContent(final ModuleDescriptor entry) {
    return entry.getContentRoots();
  }

  protected String getEntriesChooserTitle() {
    return "Modules";
  }

  protected String getDependenciesTitle() {
    return "Module dependencies";
  }

  @Override
  protected String getElementTypeName() {
    return "module";
  }

  protected String getSplitDialogChooseFilesPrompt() {
    return "&Select content roots to extract to the new module:";
  }

  protected String getNameAlreadyUsedMessage(final String name) {
    return "Module with name " + name + " already exists";
  }

  protected String getStepDescriptionText() {
    return "Please review suggested module structure for the project. At this stage you can set module names,\n" +
           "exclude particular modules from the project, merge or split individual modules.\n" +
           "All dependencies between the modules as well as dependencies on the libraries will be automatically updated.";
  }
}
