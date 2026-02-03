// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;

import javax.swing.*;
import java.awt.*;

/**
 * @deprecated please use ComboBox with browse extension, see <a href="https://plugins.jetbrains.com/docs/intellij/built-in-button.html#browse">UI guidelines</a>
 * for details
 */
@Deprecated
public class ComboboxWithBrowseButton extends ComponentWithBrowseButton<JComboBox> {
  public ComboboxWithBrowseButton() {
    super(new JComboBox(), null);
  }

  public ComboboxWithBrowseButton(JComboBox comboBox) {
    super(comboBox, null);
  }

  public JComboBox getComboBox() {
    return getChildComponent();
  }

  @Override
  public void setTextFieldPreferredWidth(final int charCount) {
    super.setTextFieldPreferredWidth(charCount);
    final Component comp = getChildComponent().getEditor().getEditorComponent();
    Dimension size = comp.getPreferredSize();
    FontMetrics fontMetrics = comp.getFontMetrics(comp.getFont());
    size.width = fontMetrics.charWidth('a') * charCount;
    comp.setPreferredSize(size);
  }

  public void addBrowseFolderListener(Project project, FileChooserDescriptor descriptor) {
    addBrowseFolderListener(project, descriptor, TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT);
  }
}
