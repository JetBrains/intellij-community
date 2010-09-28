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
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
*/
public class LibraryCompositionSettings implements Disposable {
  @NonNls private static final String DEFAULT_LIB_FOLDER = "lib";
  private final LibraryInfo[] myLibraryInfos;
  private final String myBaseDirectoryForDownloadedFiles;
  private String myDirectoryForDownloadedLibrariesPath;
  private boolean myDownloadLibraries = true;
  private LibrariesContainer.LibraryLevel myLibraryLevel = LibrariesContainer.LibraryLevel.PROJECT;
  private String myLibraryName;
  private boolean myDownloadSources = true;
  private boolean myDownloadJavadocs = true;
  private NewLibraryEditor myLibraryEditor;
  private Library mySelectedLibrary;

  public LibraryCompositionSettings(final @NotNull LibraryInfo[] libraryInfos,
                                    final @NotNull String defaultLibraryName,
                                    final @NotNull String baseDirectoryForDownloadedFiles) {
    myLibraryInfos = libraryInfos;
    myBaseDirectoryForDownloadedFiles = baseDirectoryForDownloadedFiles;
    myLibraryName = defaultLibraryName;
  }

  public void addFilesToLibrary(VirtualFile[] files, OrderRootType orderRootType) {
    final NewLibraryEditor libraryEditor = getOrCreateLibrary();
    for (VirtualFile file : files) {
      libraryEditor.addRoot(file, orderRootType);
    }
  }

  @NotNull
  public LibraryInfo[] getLibraryInfos() {
    return myLibraryInfos;
  }

  @NotNull
  public String getBaseDirectoryForDownloadedFiles() {
    return myBaseDirectoryForDownloadedFiles;
  }

  public void setDirectoryForDownloadedLibrariesPath(final String directoryForDownloadedLibrariesPath) {
    myDirectoryForDownloadedLibrariesPath = directoryForDownloadedLibrariesPath;
  }

  public boolean isDownloadLibraries() {
    return myDownloadLibraries;
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

  public void setLibraryName(final String libraryName) {
    myLibraryName = libraryName;
  }

  public String getDirectoryForDownloadedLibrariesPath() {
    if (myDirectoryForDownloadedLibrariesPath == null) {
      myDirectoryForDownloadedLibrariesPath = myBaseDirectoryForDownloadedFiles + "/" + DEFAULT_LIB_FOLDER;
    }
    return myDirectoryForDownloadedLibrariesPath;
  }

  public boolean downloadFiles(final @NotNull JComponent parent, boolean all) {
    if (myDownloadLibraries) {
      RequiredLibrariesInfo requiredLibraries = new RequiredLibrariesInfo(getLibraryInfos());

      VirtualFile[] jars = myLibraryEditor != null ? myLibraryEditor.getFiles(OrderRootType.CLASSES) : VirtualFile.EMPTY_ARRAY;
      RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = requiredLibraries.checkLibraries(jars, all);
      if (info != null) {
        LibraryDownloadInfo[] downloadingInfos = LibraryDownloader.getDownloadingInfos(info.getLibraryInfos());
        if (downloadingInfos.length > 0) {
          LibraryDownloader downloader = new LibraryDownloader(downloadingInfos, null, parent,
                                                               getDirectoryForDownloadedLibrariesPath(), myLibraryName);
          VirtualFile[] files = downloader.download();
          if (files.length != downloadingInfos.length) {
            return false;
          }
          addFilesToLibrary(files, OrderRootType.CLASSES);
        }
      }
    }
    return true;
  }


  @Nullable
  private Library createLibrary(final ModifiableRootModel rootModel, @Nullable LibrariesContainer additionalContainer) {
    if (myLibraryEditor != null) {
      VirtualFile[] roots = myLibraryEditor.getFiles(OrderRootType.CLASSES);
      return LibrariesContainerFactory.createLibrary(additionalContainer, LibrariesContainerFactory.createContainer(rootModel),
                                                     myLibraryName, myLibraryLevel, roots, VirtualFile.EMPTY_ARRAY);
    }
    return null;
  }

  public LibrariesContainer.LibraryLevel getLibraryLevel() {
    return myLibraryLevel;
  }

  public String getLibraryName() {
    return myLibraryName;
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

  @Nullable
  public NewLibraryEditor getLibraryEditor() {
    return myLibraryEditor;
  }

  @NotNull
  public NewLibraryEditor getOrCreateLibrary() {
    if (myLibraryEditor == null) {
      myLibraryEditor = new NewLibraryEditor();
    }
    return myLibraryEditor;
  }

  @Override
  public void dispose() {
  }
}
