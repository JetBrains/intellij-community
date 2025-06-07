// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.importProject;

import com.intellij.ide.JavaUiBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 */
class ModulesLayoutPanel extends ProjectLayoutPanel<ModuleDescriptor>{
  private final LibraryFilter myLibrariesFilter;

  public interface LibraryFilter {
    boolean isLibraryChosen(LibraryDescriptor libDescriptor);
  }
  ModulesLayoutPanel(ModuleInsight insight, final LibraryFilter libFilter) {
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
  protected @Unmodifiable List<ModuleDescriptor> getEntries() {
    final List<ModuleDescriptor> modules = getInsight().getSuggestedModules();
    return modules != null? modules : Collections.emptyList();
  }

  @Override
  protected Collection<Dependency> getDependencies(final ModuleDescriptor entry) {
    final List<Dependency> deps = new ArrayList<>(entry.getDependencies());
    final Collection<LibraryDescriptor> libDependencies = getInsight().getLibraryDependencies(entry);
    for (LibraryDescriptor libDependency : libDependencies) {
      if (myLibrariesFilter.isLibraryChosen(libDependency)) {
        deps.add(libDependency);
      }
    }
    return deps;
  }

  @Override
  protected @Nullable ModuleDescriptor merge(final List<? extends ModuleDescriptor> entries) {
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
  protected ModuleDescriptor split(final ModuleDescriptor entry, final String newEntryName, final Collection<? extends File> extractedData) {
    return getInsight().splitModule(entry, newEntryName, extractedData);
  }

  @Override
  protected Collection<File> getContent(final ModuleDescriptor entry) {
    return entry.getContentRoots();
  }

  @Override
  protected String getEntriesChooserTitle() {
    return JavaUiBundle.message("title.modules");
  }

  @Override
  protected @Nls(capitalization = Nls.Capitalization.Title) String getDependenciesTitle() {
    return JavaUiBundle.message("title.module.dependencies");
  }

  @Override
  protected @NotNull Set<String> getExistingNames() {
    return getInsight().getExistingModuleNames();
  }

  @Override
  protected String getElementTypeNamePlural() {
    return JavaUiBundle.message("title.modules");
  }

  @Override
  protected ElementType getElementType() {
    return ElementType.MODULE;
  }

  @Override
  protected String getSplitDialogChooseFilesPrompt() {
    return JavaUiBundle.message("label.select.content.roots.to.extract.to.new.module");
  }

  @Override
  protected String getNameAlreadyUsedMessage(final String name) {
    return JavaUiBundle.message("error.module.with.name.already.exists", name);
  }

  @Override
  protected String getStepDescriptionText() {
    return JavaUiBundle.message("module.structure.step.description");
  }
}
