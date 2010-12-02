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
package com.intellij.facet.impl.ui.libraries;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryDownloadDescription;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
*/
public class LibraryCompositionSettings implements Disposable {
  private final CustomLibraryDescription myLibraryDescription;
  private final String myBaseDirectoryPath;
  private LibrariesContainer.LibraryLevel myNewLibraryLevel;
  private NewLibraryEditor myNewLibraryEditor;
  private Library mySelectedLibrary;
  private boolean myDownloadLibraries;
  private LibraryDownloadSettings myDownloadSettings;
  private Map<Library, ExistingLibraryEditor> myExistingLibraryEditors = new HashMap<Library, ExistingLibraryEditor>();

  public LibraryCompositionSettings(final @NotNull CustomLibraryDescription libraryDescription,
                                    final @NotNull String baseDirectoryFiles) {
    myLibraryDescription = libraryDescription;
    myNewLibraryLevel = libraryDescription.getDefaultLevel();
    myBaseDirectoryPath = baseDirectoryFiles;
    final LibraryDownloadDescription downloadDescription = myLibraryDescription.getDownloadDescription();
    if (downloadDescription != null) {
      myDownloadSettings = new LibraryDownloadSettings(downloadDescription, baseDirectoryFiles);
    }
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
    return myBaseDirectoryPath;
  }

  public void setDownloadLibraries(final boolean downloadLibraries) {
    myDownloadLibraries = downloadLibraries;
  }

  public void setSelectedExistingLibrary(Library library) {
    mySelectedLibrary = library;
  }

  public void setNewLibraryLevel(final LibrariesContainer.LibraryLevel newLibraryLevel) {
    myNewLibraryLevel = newLibraryLevel;
  }

  public boolean downloadFiles(final @NotNull JComponent parent) {
    if (myDownloadLibraries && myDownloadSettings != null) {
      final NewLibraryEditor libraryEditor = myDownloadSettings.download(parent);
      if (libraryEditor != null) {
        myNewLibraryEditor = libraryEditor;
      }
    }
    return true;
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
  public Library addLibraries(final @NotNull ModifiableRootModel rootModel, final @NotNull List<Library> addedLibraries,
                              final @Nullable LibrariesContainer librariesContainer) {
    Library library = createLibrary(rootModel, librariesContainer);

    if (library != null) {
      addedLibraries.add(library);
      if (getLibraryLevel() != LibrariesContainer.LibraryLevel.MODULE) {
        rootModel.addLibraryEntry(library);
      }
    }
    if (mySelectedLibrary != null) {
      addedLibraries.add(mySelectedLibrary);
      rootModel.addLibraryEntry(mySelectedLibrary);
    }
    return library;
  }

  public void setNewLibraryEditor(NewLibraryEditor libraryEditor) {
    myNewLibraryEditor = libraryEditor;
  }

  @Override
  public void dispose() {
  }
}
