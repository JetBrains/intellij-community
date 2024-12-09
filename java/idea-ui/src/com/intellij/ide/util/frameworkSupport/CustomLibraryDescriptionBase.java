// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.frameworkSupport;

import com.intellij.framework.library.LibraryVersionProperties;
import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class CustomLibraryDescriptionBase extends CustomLibraryDescription {
  private final String myDefaultLibraryName;

  protected CustomLibraryDescriptionBase(@NotNull String defaultLibraryName) {
    myDefaultLibraryName = defaultLibraryName;
  }

  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent, VirtualFile contextDirectory) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
      .withExtensionFilter("jar")
      .withTitle(JavaUiBundle.message("new.library.file.chooser.title"))
      .withDescription(JavaUiBundle.message("new.library.file.chooser.description"));
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
