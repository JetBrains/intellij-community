/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.diagnostic.logging;

import com.intellij.diagnostic.DiagnosticBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.UIBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

/**
 * User: anna
 * Date: 05-Feb-2006
 */
public class EditLogPatternDialog extends DialogWrapper {

  private JPanel myWholePanel;
  private JTextField myNameField;
  private JCheckBox myShowFilesCombo;
  private TextFieldWithBrowseButton myFilePattern;

  public EditLogPatternDialog() {
    super(true);
    setTitle(DiagnosticBundle.message("log.monitor.edit.aliases.title"));
    init();
  }

  public void init(String name, String pattern, boolean showAll){
    myNameField.setText(name);
    myFilePattern.setText(pattern);
    myShowFilesCombo.setSelected(showAll);
    setOKActionEnabled(pattern != null && pattern.length() > 0);
  }

  @Override
  protected JComponent createCenterPanel() {
    myFilePattern.addBrowseFolderListener(UIBundle.message("file.chooser.default.title"), null, null, FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(), TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
    myFilePattern.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        setOKActionEnabled(myFilePattern.getText() != null && myFilePattern.getText().length() > 0);
      }
    });
    return myWholePanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  public boolean isShowAllFiles() {
    return myShowFilesCombo.isSelected();
  }

  public String getName(){
    final String name = myNameField.getText();
    if (name != null && name.length() > 0){
      return name;
    }
    return myFilePattern.getText();
  }

  public String getLogPattern(){
    return myFilePattern.getText();
  }


  @Override
  protected String getHelpId() {
    return "reference.run.configuration.edit.logfile.aliases";
  }
}
