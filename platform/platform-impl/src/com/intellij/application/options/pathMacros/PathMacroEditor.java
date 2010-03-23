/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.application.options.pathMacros;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.io.File;

/**
 *  @author dsl
 */
public class PathMacroEditor extends DialogWrapper {
  private JTextField myNameField;
  private JPanel myPanel;
  private TextFieldWithBrowseButton myValueField;
  private final Validator myValidator;
  private final DocumentListener myDocumentListener;

  public interface Validator {
    boolean checkName(String name);
    boolean isOK(String name, String value);
  }

  public PathMacroEditor(String title, String macroName, String value, Validator validator) {
    super(true);
    setTitle(title);
    myValidator = validator;
    myNameField.setText(macroName);
    myDocumentListener = new DocumentAdapter() {
      public void textChanged(DocumentEvent event) {
        updateControls();
      }
    };
    myNameField.getDocument().addDocumentListener(myDocumentListener);
    myValueField.setText(value);
    myValueField.addBrowseFolderListener(null, null, null, new FileChooserDescriptor(false, true, true, false, true, false), new TextComponentAccessor<JTextField>() {
      public String getText(JTextField component) {
        return component.getText();
      }

      public void setText(JTextField component, String text) {
        final int len = text.length();
        if (len > 0 && text.charAt(len - 1) == File.separatorChar) {
          text = text.substring(0, len - 1);
        }
        component.setText(text);
      }
    });
    myValueField.getTextField().getDocument().addDocumentListener(myDocumentListener);

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
      getOKAction().setEnabled(text.length() > 0 && !"/".equals(text.trim()));
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(PathMacroConfigurable.HELP_ID);
  }

  protected void doOKAction() {
    if (!myValidator.isOK(getName(), getValue())) return;
    super.doOKAction();
  }

  public String getName() {
    return myNameField.getText();
  }

  public String getValue() {
    return myValueField.getText();
  }

  protected JComponent createNorthPanel() {
    return myPanel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }
}
