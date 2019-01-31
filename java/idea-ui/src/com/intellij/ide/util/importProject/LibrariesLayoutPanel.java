// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.importProject;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class LibrariesLayoutPanel extends ProjectLayoutPanel<LibraryDescriptor>{

  public LibrariesLayoutPanel(final ModuleInsight insight) {
    super(insight);
  }

  @Override
  protected String getElementName(final LibraryDescriptor entry) {
    return entry.getName();
  }

  @Override
  protected void setElementName(final LibraryDescriptor entry, final String name) {
    entry.setName(name);
  }

  @Override
  protected List<LibraryDescriptor> getEntries() {
    final List<LibraryDescriptor> libs = getInsight().getSuggestedLibraries();
    return libs != null? libs : Collections.emptyList();
  }

  @Override
  protected Collection getDependencies(final LibraryDescriptor entry) {
    return entry.getJars();
  }

  @Override
  protected LibraryDescriptor merge(final List<LibraryDescriptor> entries) {
    final ModuleInsight insight = getInsight();
    LibraryDescriptor mainLib = null;
    for (LibraryDescriptor entry : entries) {
      if (mainLib == null) {
        mainLib = entry;
      }
      else {
        final Collection<File> files = entry.getJars();
        insight.moveJarsToLibrary(entry, files, mainLib);
      }
    }
    return mainLib;
  }

  @Override
  protected LibraryDescriptor split(final LibraryDescriptor entry, final String newEntryName, final Collection<File> extractedData) {
    return getInsight().splitLibrary(entry, newEntryName, extractedData);
  }

  @Override
  protected Collection<File> getContent(final LibraryDescriptor entry) {
    return entry.getJars();
  }

  @Override
  protected String getEntriesChooserTitle() {
    return "Libraries";
  }

  @Override
  protected String getDependenciesTitle() {
    return "Library contents";
  }

  @Override
  protected String getElementTypeName() {
    return "library";
  }

  @Override
  protected String getSplitDialogChooseFilesPrompt() {
    return "&Select jars to extract to the new library:";
  }

  @Override
  protected String getNameAlreadyUsedMessage(final String name) {
    return "library with name " + name + " already exists";
  }

  @Override
  protected String getStepDescriptionText() {
    return "Please review libraries found. At this stage you can set library names that will be used in the project,\n" +
           "exclude particular libraries from the project, or move individual files between the libraries.";
  }
}
