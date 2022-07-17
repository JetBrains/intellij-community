// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.facet.impl.ui.libraries;

import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.FrameworkLibraryVersionFilter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryDependencyScopeSuggester;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NotNullComputable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class LibraryCompositionSettings implements Disposable {
  private final CustomLibraryDescription myLibraryDescription;
  @NotNull private final NotNullComputable<String> myPathProvider;
  private FrameworkLibraryVersionFilter myVersionFilter;
  private final List<? extends FrameworkLibraryVersion> myAllVersions;
  private LibrariesContainer.LibraryLevel myNewLibraryLevel;
  private NewLibraryEditor myNewLibraryEditor;
  private Library mySelectedLibrary;
  private boolean myDownloadLibraries;
  private LibraryDownloadSettings myDownloadSettings;
  private final Map<Library, ExistingLibraryEditor> myExistingLibraryEditors = new IdentityHashMap<>();
  private FrameworkLibraryProvider myLibraryProvider;

  public LibraryCompositionSettings(final @NotNull CustomLibraryDescription libraryDescription,
                                    final @NotNull NotNullComputable<String> pathProvider,
                                    @NotNull FrameworkLibraryVersionFilter versionFilter,
                                    final List<? extends FrameworkLibraryVersion> allVersions) {
    myLibraryDescription = libraryDescription;
    myPathProvider = pathProvider;
    myVersionFilter = versionFilter;
    myNewLibraryLevel = libraryDescription.getDefaultLevel();
    myAllVersions = allVersions;
    final List<? extends FrameworkLibraryVersion> versions = getCompatibleVersions();
    if (!versions.isEmpty()) {
      myDownloadSettings = createDownloadSettings(versions.get(0));
    }
  }

  private LibraryDownloadSettings createDownloadSettings(final FrameworkLibraryVersion version) {
    return new LibraryDownloadSettings(version, myLibraryDescription.getDownloadableLibraryType(),
                                                     myNewLibraryLevel, getDefaultDownloadPath(getBaseDirectoryPath()));
  }

  public void setVersionFilter(@NotNull FrameworkLibraryVersionFilter versionFilter) {
    myVersionFilter = versionFilter;
    if (myDownloadSettings == null || !versionFilter.isAccepted(myDownloadSettings.getVersion())) {
      final FrameworkLibraryVersion newLibraryVersion = ContainerUtil.getFirstItem(getCompatibleVersions());
      if (newLibraryVersion != null) {
        myDownloadSettings = createDownloadSettings(newLibraryVersion);
      }
      else {
        myDownloadSettings = null;
      }
    }
  }

  public List<? extends FrameworkLibraryVersion> getCompatibleVersions() {
    final List<FrameworkLibraryVersion> result = new ArrayList<>();
    for (FrameworkLibraryVersion version : myAllVersions) {
      if (myVersionFilter.isAccepted(version)) {
        result.add(version);
      }
    }
    return result;
  }

  FrameworkLibraryVersionFilter getVersionFilter() {
    return myVersionFilter;
  }

  private static String getDefaultDownloadPath(@NotNull String baseDirectoryPath) {
    return baseDirectoryPath.isEmpty() ? "lib" : baseDirectoryPath + "/lib";
  }

  public void setDownloadSettings(LibraryDownloadSettings downloadSettings) {
    myDownloadSettings = downloadSettings;
  }

  public ExistingLibraryEditor getOrCreateEditor(@NotNull Library library) {
    ExistingLibraryEditor libraryEditor = myExistingLibraryEditors.get(library);
    if (libraryEditor == null) {
      libraryEditor = new ExistingLibraryEditor(library, null);
      Disposer.register(this, libraryEditor);
      myExistingLibraryEditors.put(library, libraryEditor);
    }
    return libraryEditor;
  }

  @NotNull
  public CustomLibraryDescription getLibraryDescription() {
    return myLibraryDescription;
  }

  @Nullable
  public LibraryDownloadSettings getDownloadSettings() {
    return myDownloadSettings;
  }

  @NotNull
  public String getBaseDirectoryPath() {
    return myPathProvider.compute();
  }

  public void setDownloadLibraries(final boolean downloadLibraries) {
    myDownloadLibraries = downloadLibraries;
  }

  public void setSelectedExistingLibrary(@Nullable Library library) {
    mySelectedLibrary = library;
  }

  public @Nullable Library getSelectedLibrary() {
    return mySelectedLibrary;
  }

  public void setNewLibraryLevel(final LibrariesContainer.LibraryLevel newLibraryLevel) {
    myNewLibraryLevel = newLibraryLevel;
  }

  public boolean downloadFiles(final @Nullable JComponent parent) {
    if (myDownloadLibraries && myDownloadSettings != null) {
      final NewLibraryEditor libraryEditor = myDownloadSettings.download(parent, getBaseDirectoryPath());
      if (libraryEditor != null) {
        myNewLibraryEditor = libraryEditor;
      }
    }
    return true;
  }

  public boolean isLibraryConfigured() {
    return myDownloadLibraries || myNewLibraryEditor != null || mySelectedLibrary != null || myLibraryProvider != null;
  }

  @Nullable
  private Library createLibrary(final ModifiableRootModel rootModel, @Nullable LibrariesContainer additionalContainer) {
    if (myNewLibraryEditor != null) {
      return LibrariesContainerFactory.createLibrary(additionalContainer, LibrariesContainerFactory.createContainer(rootModel),
                                                     myNewLibraryEditor, getLibraryLevel());
    }
    return null;
  }

  private LibrariesContainer.LibraryLevel getLibraryLevel() {
    return myDownloadLibraries ? myDownloadSettings.getLibraryLevel() : myNewLibraryLevel;
  }

  public LibrariesContainer.LibraryLevel getNewLibraryLevel() {
    return myNewLibraryLevel;
  }

  @Nullable
  public Library addLibraries(final @NotNull ModifiableRootModel rootModel, final @NotNull List<? super Library> addedLibraries,
                              final @Nullable LibrariesContainer librariesContainer) {
    Library newLibrary = createLibrary(rootModel, librariesContainer);

    if (newLibrary != null) {
      addedLibraries.add(newLibrary);
      DependencyScope scope = LibraryDependencyScopeSuggester.getDefaultScope(newLibrary);
      if (getLibraryLevel() != LibrariesContainer.LibraryLevel.MODULE) {
        rootModel.addLibraryEntry(newLibrary).setScope(scope);
      }
      else {
        LibraryOrderEntry orderEntry = rootModel.findLibraryOrderEntry(newLibrary);
        assert orderEntry != null;
        orderEntry.setScope(scope);
      }
    }
    if (mySelectedLibrary != null) {
      addedLibraries.add(mySelectedLibrary);
      rootModel.addLibraryEntry(mySelectedLibrary).setScope(LibraryDependencyScopeSuggester.getDefaultScope(mySelectedLibrary));
    }
    if (myLibraryProvider != null) {
      Library library = myLibraryProvider.createLibrary(myLibraryDescription.getSuitableLibraryKinds());
      addedLibraries.add(library);
      rootModel.addLibraryEntry(library).setScope(LibraryDependencyScopeSuggester.getDefaultScope(library));
    }
    return newLibrary;
  }

  public void setNewLibraryEditor(@Nullable NewLibraryEditor libraryEditor) {
    myNewLibraryEditor = libraryEditor;
  }

  public void setLibraryProvider(FrameworkLibraryProvider libraryProvider) {
    myLibraryProvider = libraryProvider;
  }

  @Override
  public void dispose() {
  }
}
