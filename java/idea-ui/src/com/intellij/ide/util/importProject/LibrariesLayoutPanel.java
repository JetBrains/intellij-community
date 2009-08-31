package com.intellij.ide.util.importProject;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 18, 2007
 */
public class LibrariesLayoutPanel extends ProjectLayoutPanel<LibraryDescriptor>{

  public LibrariesLayoutPanel(final ModuleInsight insight) {
    super(insight);
  }

  protected String getElementName(final LibraryDescriptor entry) {
    return entry.getName();
  }

  protected void setElementName(final LibraryDescriptor entry, final String name) {
    entry.setName(name);
  }

  protected List<LibraryDescriptor> getEntries() {
    final List<LibraryDescriptor> libs = getInsight().getSuggestedLibraries();
    return libs != null? libs : Collections.<LibraryDescriptor>emptyList();
  }

  protected Collection getDependencies(final LibraryDescriptor entry) {
    return entry.getJars();
  }

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

  protected LibraryDescriptor split(final LibraryDescriptor entry, final String newEntryName, final Collection<File> extractedData) {
    return getInsight().splitLibrary(entry, newEntryName, extractedData);
  }

  protected Collection<File> getContent(final LibraryDescriptor entry) {
    return entry.getJars();
  }

  protected String getEntriesChooserTitle() {
    return "Libraries";
  }

  protected String getDependenciesTitle() {
    return "Library contents";
  }

  protected String getSplitDialogTitle() {
    return "Split Library";
  }

  protected String getSplitDialogChooseFilesPrompt() {
    return "Select jars to extract to the new library:";
  }

  protected String getNameAlreadyUsedMessage(final String name) {
    return "library with name " + name + " already exists";
  }

  protected String getStepDescriptionText() {
    return "Please review libraries found. At this stage you may set library names that will be used in the project,\n" +
           "exclude particular libraries from the project, or move jar files between the libraries.";
  }
}
