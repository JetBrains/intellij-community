/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.LibraryVersionProperties;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class LibraryDownloadSettings {
  private FrameworkLibraryVersion myVersion;
  private final DownloadableLibraryType myLibraryType;
  private String myDirectoryForDownloadedLibrariesPath;
  private final String myLibraryName;
  private final boolean myDownloadSources;
  private final boolean myDownloadJavaDocs;
  private final LibrariesContainer.LibraryLevel myLibraryLevel;
  private final List<? extends DownloadableFileDescription> mySelectedDownloads;

  public LibraryDownloadSettings(@NotNull FrameworkLibraryVersion libraryVersion,
                                 @Nullable DownloadableLibraryType libraryType,
                                 final LibrariesContainer.LibraryLevel libraryLevel, final String downloadedLibrariesPath) {
    this(libraryVersion, libraryType, downloadedLibrariesPath, libraryVersion.getDefaultLibraryName(), libraryLevel,
         libraryVersion.getFiles(), true, true);
  }

  public LibraryDownloadSettings(@NotNull FrameworkLibraryVersion libraryVersion, @Nullable DownloadableLibraryType libraryType,
                                 @NotNull String directoryForDownloadedLibrariesPath, @NotNull String libraryName,
                                 @NotNull LibrariesContainer.LibraryLevel libraryLevel,
                                 @NotNull List<? extends DownloadableFileDescription> selectedDownloads,
                                 boolean downloadSources, boolean downloadJavaDocs) {
    myVersion = libraryVersion;
    myLibraryType = libraryType;
    myDirectoryForDownloadedLibrariesPath = directoryForDownloadedLibrariesPath;
    myLibraryName = libraryName;
    myDownloadSources = downloadSources;
    myDownloadJavaDocs = downloadJavaDocs;
    myLibraryLevel = libraryLevel;
    mySelectedDownloads = selectedDownloads;
  }

  @NotNull
  public FrameworkLibraryVersion getVersion() {
    return myVersion;
  }

  public boolean isDownloadJavaDocs() {
    return myDownloadJavaDocs;
  }

  public boolean isDownloadSources() {
    return myDownloadSources;
  }

  public String getLibraryName() {
    return myLibraryName;
  }

  public String getDirectoryForDownloadedLibrariesPath() {
    return myDirectoryForDownloadedLibrariesPath;
  }

  public List<? extends DownloadableFileDescription> getSelectedDownloads() {
    return mySelectedDownloads;
  }

  @NotNull
  public LibrariesContainer.LibraryLevel getLibraryLevel() {
    return myLibraryLevel;
  }

  public DownloadableLibraryType getLibraryType() {
    return myLibraryType;
  }

  public void setVersion(FrameworkLibraryVersion version) {
    myVersion = version;
  }

  public void setDirectoryForDownloadedLibrariesPath(String directoryForDownloadedLibrariesPath) {
    myDirectoryForDownloadedLibrariesPath = directoryForDownloadedLibrariesPath;
  }

  @Nullable
  public NewLibraryEditor download(JComponent parent) {
    VirtualFile[] files = DownloadableFileService.getInstance().createDownloader(mySelectedDownloads, null, parent, myLibraryName + " Library")
      .toDirectory(myDirectoryForDownloadedLibrariesPath)
      .download();
    if (files == null) {
      return null;
    }

    final NewLibraryEditor libraryEditor;
    if (myLibraryType != null) {
      libraryEditor = new NewLibraryEditor(myLibraryType, new LibraryVersionProperties(myVersion.getVersionString()));
    }
    else {
      libraryEditor = new NewLibraryEditor();
    }
    libraryEditor.setName(myLibraryName);
    for (VirtualFile file : files) {
      libraryEditor.addRoot(file, OrderRootType.CLASSES);
    }
    return libraryEditor;
  }
}
