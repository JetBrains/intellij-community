/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;

import javax.swing.*;

public class ComboboxWithBrowseButton extends ComponentWithBrowseButton<JComboBox> {
  public ComboboxWithBrowseButton() {
    super(new JComboBox(), null);
  }

  public JComboBox getComboBox() {
    return getChildComponent();
  }

  public void addBrowseFolderListener(Project project, FileChooserDescriptor descriptor) {
    addBrowseFolderListener(null, null, project, descriptor, TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);
  }
}
