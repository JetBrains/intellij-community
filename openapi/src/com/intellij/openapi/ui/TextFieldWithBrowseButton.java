/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class TextFieldWithBrowseButton extends ComponentWithBrowseButton<JTextField> {
  public TextFieldWithBrowseButton(){
    this(null);
  }

  public TextFieldWithBrowseButton(ActionListener browseActionListener) {
    super(new JTextField(), browseActionListener);
  }

  public void addBrowseFolderListener(String title, String description, Project project, FileChooserDescriptor fileChooserDescriptor) {
    addBrowseFolderListener(title, description, project, fileChooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
  }

  public JTextField getTextField() {
    return getChildComponent();
  }

  /**
   * @return trimmed text
   */
  public String getText(){
    return getTextField().getText();
  }

  public void setText(final String text){
    getTextField().setText(text);
  }

  public void setTextFieldPreferredWidth(final int charCount) {
    Dimension size = getTextField().getPreferredSize();
    FontMetrics fontMetrics = getTextField().getFontMetrics(getTextField().getFont());
    size.width = fontMetrics.charWidth('a') * charCount;
    getTextField().setPreferredSize(size);
  }

  public boolean isEditable() {
    return getTextField().isEditable();
  }

  public void setEditable(boolean b) {
    getTextField().setEditable(b);
  }
}
