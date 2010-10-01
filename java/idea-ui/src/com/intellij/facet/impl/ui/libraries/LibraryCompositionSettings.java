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

import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryDownloadDescription;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
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
  @NonNls private static final String DEFAULT_LIB_FOLDER = "lib";
  private final CustomLibraryDescription myLibraryDescription;
  private final String myBaseDirectoryForDownloadedFiles;
  private String myDirectoryForDownloadedLibrariesPath;
  private boolean myDownloadLibraries = true;
  private LibrariesContainer.LibraryLevel myLibraryLevel = LibrariesContainer.LibraryLevel.PROJECT;
  private String myDownloadedLibraryName;
  private boolean myDownloadSources = true;
  private boolean myDownloadJavadocs = true;
  private List<LibraryDownloadInfo> mySelectedDownloads;
  private NewLibraryEditor myNewLibraryEditor;
  private Library mySelectedLibrary;
  private Map<Library, ExistingLibraryEditor> myExistingLibraryEditors = new HashMap<Library, ExistingLibraryEditor>();

  public LibraryCompositionSettings(final @NotNull CustomLibraryDescription libraryDescription,
                                    final @NotNull String baseDirectoryForDownloadedFiles) {
    myLibraryDescription = libraryDescription;
    myBaseDirectoryForDownloadedFiles = baseDirectoryForDownloadedFiles;
    final LibraryDownloadDescription downloadDescription = myLibraryDescription.getDownloadDescription();
    if (downloadDescription != null) {
      myDownloadedLibraryName = downloadDescription.getDefaultLibraryName();
      mySelectedDownloads = downloadDescription.getDownloads();
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

  @NotNull
  public String getBaseDirectoryForDownloadedFiles() {
    return myBaseDirectoryForDownloadedFiles;
  }

  public void setDirectoryForDownloadedLibrariesPath(final String directoryForDownloadedLibrariesPath) {
    myDirectoryForDownloadedLibrariesPath = directoryForDownloadedLibrariesPath;
  }

  public void setDownloadLibraries(final boolean downloadLibraries) {
    myDownloadLibraries = downloadLibraries;
  }

  public void setSelectedExistingLibrary(Library library) {
    mySelectedLibrary = library;
  }

  public void setLibraryLevel(final LibrariesContainer.LibraryLevel libraryLevel) {
    myLibraryLevel = libraryLevel;
  }

  public void setDownloadedLibraryName(final String downloadedLibraryName) {
    myDownloadedLibraryName = downloadedLibraryName;
  }

  public String getDirectoryForDownloadedLibrariesPath() {
    if (myDirectoryForDownloadedLibrariesPath == null) {
      myDirectoryForDownloadedLibrariesPath = myBaseDirectoryForDownloadedFiles + "/" + DEFAULT_LIB_FOLDER;
    }
    return myDirectoryForDownloadedLibrariesPath;
  }

  public List<LibraryDownloadInfo> getSelectedDownloads() {
    return mySelectedDownloads;
  }

  public void setSelectedDownloads(List<LibraryDownloadInfo> selectedDownloads) {
    mySelectedDownloads = selectedDownloads;
  }

  public boolean downloadFiles(final @NotNull JComponent parent) {
    final LibraryDownloadDescription downloadDescription = myLibraryDescription.getDownloadDescription();
    if (myDownloadLibraries && downloadDescription != null) {
      LibraryDownloadInfo[] toDownload = mySelectedDownloads.toArray(new LibraryDownloadInfo[mySelectedDownloads.size()]);
      LibraryDownloader downloader = new LibraryDownloader(toDownload, null, parent,
                                                           getDirectoryForDownloadedLibrariesPath(), myDownloadedLibraryName);
      VirtualFile[] files = downloader.download();
      if (files.length != toDownload.length) {
        return false;
      }
      myNewLibraryEditor = new NewLibraryEditor();
      myNewLibraryEditor.setName(myDownloadedLibraryName);
      for (VirtualFile file : files) {
        myNewLibraryEditor.addRoot(file, OrderRootType.CLASSES);
      }
    }
    return true;
  }


  @Nullable
  private Library createLibrary(final ModifiableRootModel rootModel, @Nullable LibrariesContainer additionalContainer) {
    if (myNewLibraryEditor != null) {
      VirtualFile[] roots = myNewLibraryEditor.getFiles(OrderRootType.CLASSES);
      return LibrariesContainerFactory.createLibrary(additionalContainer, LibrariesContainerFactory.createContainer(rootModel),
                                                     myNewLibraryEditor.getName(), myLibraryLevel, roots, VirtualFile.EMPTY_ARRAY);
    }
    return null;
  }

  public LibrariesContainer.LibraryLevel getLibraryLevel() {
    return myLibraryLevel;
  }

  public String getDownloadedLibraryName() {
    return myDownloadedLibraryName;
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

  public boolean isDownloadSources() {
    return myDownloadSources;
  }

  public void setDownloadSources(boolean downloadSources) {
    myDownloadSources = downloadSources;
  }

  public boolean isDownloadJavadocs() {
    return myDownloadJavadocs;
  }

  public void setDownloadJavadocs(boolean downloadJavadocs) {
    myDownloadJavadocs = downloadJavadocs;
  }

  public void setNewLibraryEditor(NewLibraryEditor libraryEditor) {
    myNewLibraryEditor = libraryEditor;
  }

  @Override
  public void dispose() {
  }
}
