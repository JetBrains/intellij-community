/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.framework.library.LibraryVersionProperties;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class CustomLibraryDescriptionBase extends CustomLibraryDescription {
  private final String myDefaultLibraryName;

  protected CustomLibraryDescriptionBase(@NotNull String defaultLibraryName) {
    myDefaultLibraryName = defaultLibraryName;
  }

  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent, VirtualFile contextDirectory) {
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, false, true, false, false, true);
    descriptor.setTitle(IdeBundle.message("new.library.file.chooser.title"));
    descriptor.setDescription(IdeBundle.message("new.library.file.chooser.description"));
    final VirtualFile[] files = FileChooser.chooseFiles(descriptor, parentComponent, null, contextDirectory);
    if (files.length == 0) {
      return null;
    }
    return new NewLibraryConfiguration(myDefaultLibraryName, getDownloadableLibraryType(), new LibraryVersionProperties()) {
      @Override
      public void addRoots(@NotNull LibraryEditor editor) {
        for (VirtualFile file : files) {
          editor.addRoot(file, OrderRootType.CLASSES);
        }
      }
    };
  }
}
