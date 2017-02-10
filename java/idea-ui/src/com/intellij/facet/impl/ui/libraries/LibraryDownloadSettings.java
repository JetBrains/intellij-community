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

import com.intellij.framework.library.DownloadableLibraryFileDescription;
import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.framework.library.FrameworkLibraryVersion;
import com.intellij.framework.library.LibraryVersionProperties;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class LibraryDownloadSettings {
  private final FrameworkLibraryVersion myVersion;
  private final DownloadableLibraryType myLibraryType;
  private String myLibrariesPath;
  private final String myLibraryName;
  private final boolean myDownloadSources;
  private final boolean myDownloadJavaDocs;
  private final LibrariesContainer.LibraryLevel myLibraryLevel;
  private final List<? extends DownloadableLibraryFileDescription> mySelectedDownloads;

  public LibraryDownloadSettings(@NotNull FrameworkLibraryVersion libraryVersion,
                                 @Nullable DownloadableLibraryType libraryType,
                                 final LibrariesContainer.LibraryLevel libraryLevel, final String downloadedLibrariesPath) {
    this(libraryVersion, libraryType, downloadedLibrariesPath, libraryVersion.getDefaultLibraryName(), libraryLevel,
         getRequiredFiles(libraryVersion.getFiles()), true, true);
  }

  public LibraryDownloadSettings(@NotNull FrameworkLibraryVersion libraryVersion, @Nullable DownloadableLibraryType libraryType,
                                 @NotNull String librariesPath, @NotNull String libraryName,
                                 @NotNull LibrariesContainer.LibraryLevel libraryLevel,
                                 @NotNull List<? extends DownloadableLibraryFileDescription> selectedDownloads,
                                 boolean downloadSources, boolean downloadJavaDocs) {
    myVersion = libraryVersion;
    myLibraryType = libraryType;
    myLibrariesPath = librariesPath;
    myLibraryName = libraryName;
    myDownloadSources = downloadSources;
    myDownloadJavaDocs = downloadJavaDocs;
    myLibraryLevel = libraryLevel;
    mySelectedDownloads = selectedDownloads;
  }

  private static List<? extends DownloadableLibraryFileDescription> getRequiredFiles(List<? extends DownloadableLibraryFileDescription> files) {
    return ContainerUtil.filter(files, new Condition<DownloadableLibraryFileDescription>() {
      @Override
      public boolean value(DownloadableLibraryFileDescription description) {
        return !description.isOptional();
      }
    });
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
    return myLibrariesPath;
  }

  public List<? extends DownloadableLibraryFileDescription> getSelectedDownloads() {
    return mySelectedDownloads;
  }

  @NotNull
  public LibrariesContainer.LibraryLevel getLibraryLevel() {
    return myLibraryLevel;
  }

  public DownloadableLibraryType getLibraryType() {
    return myLibraryType;
  }

  @Nullable
  public NewLibraryEditor download(JComponent parent, @Nullable String rootPath) {
    final List<DownloadableFileDescription> toDownload = new ArrayList<>(mySelectedDownloads);
    Map<DownloadableFileDescription, OrderRootType> rootTypes = new HashMap<>();
    for (DownloadableLibraryFileDescription description : mySelectedDownloads) {
      final DownloadableFileDescription sources = description.getSourcesDescription();
      if (myDownloadSources && sources != null) {
        toDownload.add(sources);
        rootTypes.put(sources, OrderRootType.SOURCES);
      }
      final DownloadableFileDescription docs = description.getDocumentationDescription();
      if (myDownloadJavaDocs && docs != null) {
        toDownload.add(docs);
        rootTypes.put(docs, JavadocOrderRootType.getInstance());
      }
    }

    String path = rootPath != null && !FileUtil.isAbsolute(myLibrariesPath) ? new File(rootPath, myLibrariesPath).getPath() : myLibrariesPath;
    List<Pair<VirtualFile,DownloadableFileDescription>> downloaded =
      DownloadableFileService.getInstance()
        .createDownloader(toDownload, myLibraryName + " Library")
        .downloadWithProgress(path, null, parent);
    if (downloaded == null) {
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
    for (Pair<VirtualFile, DownloadableFileDescription> pair : downloaded) {
      final OrderRootType rootType = rootTypes.containsKey(pair.getSecond()) ? rootTypes.get(pair.getSecond()) : OrderRootType.CLASSES;
      libraryEditor.addRoot(pair.getFirst(), rootType);
    }
    return libraryEditor;
  }
}
