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

import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryDownloadDescription;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public class LibraryDownloadSettings {
  private final LibraryDownloadDescription myDescription;
  private String myDirectoryForDownloadedLibrariesPath;
  private String myLibraryName;
  private boolean myDownloadSources = true;
  private boolean myDownloadJavadocs = true;
  private LibrariesContainer.LibraryLevel myLibraryLevel = LibrariesContainer.LibraryLevel.PROJECT;
  private List<LibraryDownloadInfo> mySelectedDownloads;

  public LibraryDownloadSettings(LibraryDownloadDescription description, String baseDirectoryForDownloadedFiles) {
    myDescription = description;
    myDirectoryForDownloadedLibrariesPath = baseDirectoryForDownloadedFiles + "/lib";
    myLibraryName = description.getDefaultLibraryName();
    mySelectedDownloads = description.getDownloads();
  }

  public LibraryDownloadDescription getDescription() {
    return myDescription;
  }

  public boolean isDownloadJavadocs() {
    return myDownloadJavadocs;
  }

  public void setDownloadJavadocs(boolean downloadJavadocs) {
    myDownloadJavadocs = downloadJavadocs;
  }

  public boolean isDownloadSources() {
    return myDownloadSources;
  }

  public void setDownloadSources(boolean downloadSources) {
    myDownloadSources = downloadSources;
  }

  public String getLibraryName() {
    return myLibraryName;
  }

  public void setLibraryName(String libraryName) {
    myLibraryName = libraryName;
  }

  public String getDirectoryForDownloadedLibrariesPath() {
    return myDirectoryForDownloadedLibrariesPath;
  }

  public void setDirectoryForDownloadedLibrariesPath(String directoryForDownloadedLibrariesPath) {
    myDirectoryForDownloadedLibrariesPath = directoryForDownloadedLibrariesPath;
  }

  public List<LibraryDownloadInfo> getSelectedDownloads() {
    return mySelectedDownloads;
  }

  public void setSelectedDownloads(List<LibraryDownloadInfo> selectedDownloads) {
    mySelectedDownloads = selectedDownloads;
  }

  public LibrariesContainer.LibraryLevel getLibraryLevel() {
    return myLibraryLevel;
  }

  public void setLibraryLevel(LibrariesContainer.LibraryLevel libraryLevel) {
    myLibraryLevel = libraryLevel;
  }

  @Nullable
  public NewLibraryEditor download(JComponent parent) {
    LibraryDownloadInfo[] toDownload = mySelectedDownloads.toArray(new LibraryDownloadInfo[mySelectedDownloads.size()]);
    LibraryDownloader downloader = new LibraryDownloader(toDownload, null, parent, myDirectoryForDownloadedLibrariesPath, myLibraryName);
    VirtualFile[] files = downloader.download();
    if (files.length != toDownload.length) {
      return null;
    }

    final NewLibraryEditor libraryEditor = new NewLibraryEditor();
    libraryEditor.setName(myLibraryName);
    for (VirtualFile file : files) {
      libraryEditor.addRoot(file, OrderRootType.CLASSES);
    }
    return libraryEditor;
  }
}
