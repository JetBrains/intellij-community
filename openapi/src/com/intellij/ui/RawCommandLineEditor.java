/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class RawCommandLineEditor extends JPanel {
  private final TextFieldWithBrowseButton myTextField;
  private String myDialodCaption = "";
  private static final Icon OPEN_EDIT_DIALOG_ICOON = IconLoader.getIcon("/actions/showViewer.png");

  public RawCommandLineEditor() {
    super(new BorderLayout());
    myTextField = new TextFieldWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JTextArea textArea = new JTextArea(10, 50);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setDocument(myTextField.getTextField().getDocument());
        InsertPathAction.copyFromTo(myTextField.getTextField(), textArea);
        DialogBuilder builder = new DialogBuilder(myTextField);
        JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(textArea);
        builder.setDimensionServiceKey("EditParametersPopupWindow");
        builder.setCenterPanel(scrollPane);
        builder.setPreferedFocusComponent(textArea);
        String rawText = myDialodCaption;
        if (StringUtil.endsWithChar(rawText, ':')) rawText = rawText.substring(0, rawText.length() - 1);
        builder.setTitle(rawText);
        builder.addCloseButton();
        builder.show();
      }
    });
    myTextField.setButtonIcon(OPEN_EDIT_DIALOG_ICOON);
    add(myTextField, BorderLayout.CENTER);
    setDescriptor(null);
  }

  public void setDescriptor(FileChooserDescriptor descriptor) {
    InsertPathAction.addTo(myTextField.getTextField(), descriptor);
  }

  public String getDialodCaption() {
    return myDialodCaption;
  }

  public void setDialodCaption(String dialodCaption) {
    myDialodCaption = dialodCaption != null ? dialodCaption : "";
  }

  public void setText(String text) {
    myTextField.setText(text);
  }

  public String getText() {
    return myTextField.getText();
  }

  public JTextField getTextField() {
    return myTextField.getTextField();
  }

  public Document getDocument() {
    return myTextField.getTextField().getDocument();
  }

  public void attachLabel(JLabel label) {
    label.setLabelFor(myTextField.getTextField());
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myTextField.setEnabled(enabled);
  }
}
