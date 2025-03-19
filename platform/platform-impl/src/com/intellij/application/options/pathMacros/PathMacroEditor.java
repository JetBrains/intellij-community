// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.pathMacros;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;
import java.io.IOException;

public final class PathMacroEditor extends DialogWrapper {
  private JTextField myNameField;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myValueField;
  private final Validator myValidator;

  public interface Validator {
    boolean checkName(String name);
    boolean isOK(String name, String value);
  }

  public PathMacroEditor(@NlsContexts.DialogTitle String title, @NlsSafe String macroName, @NlsSafe String value, Validator validator) {
    super(true);
    setTitle(title);
    myValidator = validator;
    myNameField.setText(macroName);
    DocumentListener documentListener = new DocumentAdapter() {
      @Override
      public void textChanged(@NotNull DocumentEvent event) {
        updateControls();
      }
    };
    myNameField.getDocument().addDocumentListener(documentListener);
    myValueField.setText(value);
    var descriptor = new FileChooserDescriptor(false, true, true, false, true, false);
    myValueField.addBrowseFolderListener(null, descriptor, new TextComponentAccessor<>() {
      @Override
      public String getText(JTextField component) {
        return component.getText();
      }

      @Override
      public void setText(JTextField component, @NotNull String text) {
        final int len = text.length();
        if (len > 0 && text.charAt(len - 1) == File.separatorChar) {
          text = text.substring(0, len - 1);
        }
        component.setText(text);
      }
    });
    myValueField.getTextField().getDocument().addDocumentListener(documentListener);

    init();
    updateControls();
  }

  public void setMacroNameEditable(boolean isEditable) {
    myNameField.setEditable(isEditable);
  }

  private void updateControls() {
    final boolean isNameOK = myValidator.checkName(myNameField.getText());
    getOKAction().setEnabled(isNameOK);
    if (isNameOK) {
      final String text = myValueField.getText().trim();
      getOKAction().setEnabled(!text.isEmpty() && !"/".equals(text.trim()));
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  @Override
  protected String getHelpId() {
    return PathMacroConfigurable.HELP_ID;
  }

  @Override
  protected void doOKAction() {
    if (!myValidator.isOK(getName(), getValue())) return;
    super.doOKAction();
  }

  public String getName() {
    return myNameField.getText().trim();
  }

  public String getValue() {
    String path = myValueField.getText().trim();
    File file = new File(path);
    if (file.isAbsolute()) {
      try {
        return file.getCanonicalPath();
      }
      catch (IOException ignored) {
      }
    }
    return path;
  }

  @Override
  protected JComponent createNorthPanel() {
    return myPanel;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }
}
