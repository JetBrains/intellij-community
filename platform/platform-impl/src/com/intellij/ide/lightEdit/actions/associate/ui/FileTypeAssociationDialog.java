// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate.ui;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class FileTypeAssociationDialog extends DialogWrapper {
  private FileTypeAssociationForm myForm;

  public FileTypeAssociationDialog() {
    super(false);
    init();
    setTitle("Associate File Types");
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    myForm = new FileTypeAssociationForm();
    return myForm.getTopPanel();
  }

  public List<FileType> getSelectedFileTypes() {
    return myForm.getSelectedFileTypes();
  }
}
