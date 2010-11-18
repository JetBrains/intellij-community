// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.execution.util;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public class ExecutableDialog extends DialogWrapper {
  private JPanel myCenterPanel;
  private TextFieldWithBrowseButton myExecutablePath;
  private JLabel myInfoLabel;
  private final ExecutableValidator myExecutableValidator;
  private String myErrorNotValidText;

  public ExecutableDialog(Project project, ExecutableValidator executableValidator) {
    super(project, false);
    myExecutableValidator = executableValidator;
    myErrorNotValidText = executableValidator.getDialogErrorText();
    init();
    updateUI();
    setTitle(myExecutableValidator.getDialogTitle());
    myInfoLabel.setText(myExecutableValidator.getDialogDescription());
  }

  @Override
  protected JComponent createCenterPanel() {
    return myCenterPanel;
  }

  // disable OK button if current executable is not valid
  private void updateUI() {
    final String path = myExecutablePath.getText();
    if (myExecutableValidator.isExecutableValid(path)) {
      setErrorText(null);
      getOKAction().setEnabled(true);
    } else {
      setErrorText(myErrorNotValidText);
      getOKAction().setEnabled(false);
    }
  }

  private void createUIComponents() {
    myExecutablePath = new TextFieldWithBrowseButton();
    myExecutablePath.setText(myExecutableValidator.getCurrentExecutable());
    FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
    myExecutablePath.addBrowseFolderListener(myExecutableValidator.getFileChooserTitle(), myExecutableValidator.getFileChooserDescription(),
                                             null, descriptor);
    myExecutablePath.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        updateUI();
      }
    });
  }

  public String getPath() {
    return myExecutablePath.getText();
  }
}