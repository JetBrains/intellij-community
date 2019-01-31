// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  protected String getElementName(final ModuleDescriptor entry) {
    return entry.getName();
  }

  @Override
  protected void setElementName(final ModuleDescriptor entry, final String name) {
    entry.setName(name);
  }

  @Override
  protected List<ModuleDescriptor> getEntries() {
    final List<ModuleDescriptor> modules = getInsight().getSuggestedModules();
    return modules != null? modules : Collections.emptyList();
  }

  @Override
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

  @Override
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

  @Override
  protected ModuleDescriptor split(final ModuleDescriptor entry, final String newEntryName, final Collection<File> extractedData) {
    return getInsight().splitModule(entry, newEntryName, extractedData);
  }

  @Override
  protected Collection<File> getContent(final ModuleDescriptor entry) {
    return entry.getContentRoots();
  }

  @Override
  protected String getEntriesChooserTitle() {
    return "Modules";
  }

  @Override
  protected String getDependenciesTitle() {
    return "Module dependencies";
  }

  @Override
  protected String getElementTypeName() {
    return "module";
  }

  @Override
  protected String getSplitDialogChooseFilesPrompt() {
    return "&Select content roots to extract to the new module:";
  }

  @Override
  protected String getNameAlreadyUsedMessage(final String name) {
    return "Module with name " + name + " already exists";
  }

  @Override
  protected String getStepDescriptionText() {
    return "Please review suggested module structure for the project. At this stage you can set module names,\n" +
           "exclude particular modules from the project, merge or split individual modules.\n" +
           "All dependencies between the modules as well as dependencies on the libraries will be automatically updated.";
  }
}
