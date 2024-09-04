// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TextBrowseFolderListener extends ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> {
  public TextBrowseFolderListener(@NotNull FileChooserDescriptor fileChooserDescriptor) {
    this(fileChooserDescriptor, null);
  }

  public TextBrowseFolderListener(@NotNull FileChooserDescriptor fileChooserDescriptor, @Nullable Project project) {
    super(null, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
  }

  void setOwnerComponent(@NotNull TextFieldWithBrowseButton component) {
    myTextComponent = component.getChildComponent();
  }

  FileChooserDescriptor getFileChooserDescriptor() {
    return myFileChooserDescriptor;
  }
}
