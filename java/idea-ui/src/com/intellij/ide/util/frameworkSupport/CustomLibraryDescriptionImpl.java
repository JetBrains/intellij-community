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
package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.impl.ui.libraries.RequiredLibrariesInfo;
import com.intellij.facet.ui.libraries.LibraryDownloadInfo;
import com.intellij.facet.ui.libraries.LibraryInfo;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ui.configuration.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryDownloadDescription;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class CustomLibraryDescriptionImpl extends CustomLibraryDescription {
  private final LibraryInfo[] myLibraryInfos;
  private String myDefaultLibraryName;
  private final LibraryDownloadDescription myDownloadDescription;
  private final Condition<List<VirtualFile>> mySuitableLibraryCondition;

  public CustomLibraryDescriptionImpl(@NotNull LibraryInfo[] libraryInfos, @NotNull String defaultLibraryName) {
    myLibraryInfos = libraryInfos;
    myDefaultLibraryName = defaultLibraryName;
    List<LibraryDownloadInfo> downloads = new ArrayList<LibraryDownloadInfo>();
    for (LibraryInfo info : libraryInfos) {
      ContainerUtil.addIfNotNull(downloads, info.getDownloadingInfo());
    }
    myDownloadDescription = !downloads.isEmpty() ? new LibraryDownloadDescription(defaultLibraryName, downloads) : null;
    mySuitableLibraryCondition = new Condition<List<VirtualFile>>() {
      @Override
      public boolean value(List<VirtualFile> virtualFiles) {
        RequiredLibrariesInfo info = new RequiredLibrariesInfo(myLibraryInfos);
        return info.checkLibraries(virtualFiles) == null;
      }
    };
  }

  @Override
  public LibraryDownloadDescription getDownloadDescription() {
    return myDownloadDescription;
  }

  @NotNull
  @Override
  public Condition<List<VirtualFile>> getSuitableLibraryCondition() {
    return mySuitableLibraryCondition;
  }

  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent, VirtualFile contextDirectory) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, false, false, true);
    descriptor.setTitle(IdeBundle.message("new.library.file.chooser.title"));
    descriptor.setDescription(IdeBundle.message("new.library.file.chooser.description"));
    final VirtualFile[] files = FileChooser.chooseFiles(parentComponent, descriptor, contextDirectory);
    if (files.length == 0) {
      return null;
    }
    return new NewLibraryConfiguration(myDefaultLibraryName) {
      @Override
      public void addRoots(@NotNull LibraryEditor editor) {
        for (VirtualFile file : files) {
          editor.addRoot(file, OrderRootType.CLASSES);
        }
      }
    };
  }
}
