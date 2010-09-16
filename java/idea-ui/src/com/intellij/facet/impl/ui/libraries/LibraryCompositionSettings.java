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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author nik
*/
public class LibraryCompositionSettings implements Disposable {

  @NonNls private static final String DEFAULT_LIB_FOLDER = "lib";
  private final LibraryInfo[] myLibraryInfos;
  private final String myBaseDirectoryForDownloadedFiles;
  private final String myTitle;
  private String myDirectoryForDownloadedLibrariesPath;
  private boolean myDownloadLibraries = true;
  private final Set<Library> myUsedLibraries = new LinkedHashSet<Library>();
  private LibrariesContainer.LibraryLevel myLibraryLevel = LibrariesContainer.LibraryLevel.PROJECT;
  private String myLibraryName;
  private final Icon myIcon;
  private boolean myDownloadSources = true;
  private boolean myDownloadJavadocs = true;
  private Library myLibrary;

  public LibraryCompositionSettings(final @NotNull LibraryInfo[] libraryInfos,
                                    final @NotNull String defaultLibraryName,
                                    final @NotNull String baseDirectoryForDownloadedFiles,
                                    final String title, @Nullable Icon icon) {
    myLibraryInfos = libraryInfos;
    myBaseDirectoryForDownloadedFiles = baseDirectoryForDownloadedFiles;
    myTitle = title;
    myLibraryName = defaultLibraryName;
    myIcon = icon;
  }

  public void addFilesToLibrary(VirtualFile[] files, OrderRootType orderRootType) {
    final Library.ModifiableModel modifiableModel = getOrCreateLibrary().getModifiableModel();
    for (VirtualFile file : files) {
      modifiableModel.addRoot(file, orderRootType);
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        modifiableModel.commit();
      }
    });
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

  public void setUsedLibraries(Collection<Library> addedLibraries) {
    myUsedLibraries.clear();
    myUsedLibraries.addAll(addedLibraries);
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

  public String getTitle() {
    return myTitle;
  }

  public boolean downloadFiles(final @NotNull LibraryDownloadingMirrorsMap mirrorsMap,
                               @NotNull LibrariesContainer librariesContainer, final @NotNull JComponent parent,
                               boolean all) {
    if (myDownloadLibraries) {
      RequiredLibrariesInfo requiredLibraries = new RequiredLibrariesInfo(getLibraryInfos());

      List<VirtualFile> roots = new ArrayList<VirtualFile>();
      for (Library library : myUsedLibraries) {
        ContainerUtil.addAll(roots, librariesContainer.getLibraryFiles(library, OrderRootType.CLASSES));
      }
      VirtualFile[] jars = VfsUtil.toVirtualFileArray(roots);
      RequiredLibrariesInfo.RequiredClassesNotFoundInfo info = requiredLibraries.checkLibraries(jars, all);
      if (info != null) {
        LibraryDownloadInfo[] downloadingInfos = LibraryDownloader.getDownloadingInfos(info.getLibraryInfos());
        if (downloadingInfos.length > 0) {
          LibraryDownloader downloader = new LibraryDownloader(downloadingInfos, null, parent,
                                                               getDirectoryForDownloadedLibrariesPath(), myLibraryName,
                                                               mirrorsMap);
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
    if (myLibrary != null) {
      VirtualFile[] roots = myLibrary.getFiles(OrderRootType.CLASSES);
      myLibrary.dispose();
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

  public Collection<Library> getUsedLibraries() {
    return Collections.unmodifiableCollection(myUsedLibraries);
  }

  public Icon getIcon() {
    return myIcon;
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
    for (Library usedLibrary : getUsedLibraries()) {
      addedLibraries.add(usedLibrary);
      rootModel.addLibraryEntry(usedLibrary);
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
  public Library getLibrary() {
    return myLibrary;
  }

  @NotNull
  public Library getOrCreateLibrary() {
    if (myLibrary == null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          myLibrary = new ApplicationLibraryTable().createLibrary();
        }
      });
    }
    return myLibrary;
  }

  @Override
  public void dispose() {
    System.out.println("I'm disposed!");
  }
}
