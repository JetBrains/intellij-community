/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.compiler.impl.javaCompiler.jikes;

import com.intellij.compiler.options.ComparingUtils;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;
import org.jetbrains.jps.model.java.compiler.JikesCompilerOptions;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public class JikesConfigurable implements Configurable {
  private JPanel myPanel;
  private JCheckBox myCbDebuggingInfo;
  private JCheckBox myCbDeprecation;
  private JCheckBox myCbGenerateNoWarnings;
  private RawCommandLineEditor myAdditionalOptionsField;
  private TextFieldWithBrowseButton myPathField;
  private final JikesCompilerOptions myJikesSettings;

  public JikesConfigurable(JikesCompilerOptions jikesSettings) {
    myJikesSettings = jikesSettings;
    myPathField.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        VirtualFile file = FileChooser.chooseFile(descriptor, myPathField, null, null);
        if (file != null) {
          myPathField.setText(file.getPath().replace('/', File.separatorChar));
        }
      }
    });
    myAdditionalOptionsField.setDialogCaption(CompilerBundle.message("java.compiler.option.additional.command.line.parameters"));
  }

  public String getDisplayName() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    boolean isModified = false;
    isModified |= ComparingUtils.isModified(myPathField, myJikesSettings.JIKES_PATH.replace('/', File.separatorChar));
    isModified |= ComparingUtils.isModified(myCbDeprecation, myJikesSettings.DEPRECATION);
    isModified |= ComparingUtils.isModified(myCbDebuggingInfo, myJikesSettings.DEBUGGING_INFO);
    isModified |= ComparingUtils.isModified(myCbGenerateNoWarnings, myJikesSettings.GENERATE_NO_WARNINGS);
    isModified |= ComparingUtils.isModified(myAdditionalOptionsField, myJikesSettings.ADDITIONAL_OPTIONS_STRING);
    return isModified;
  }

  public void apply() throws ConfigurationException {
    myJikesSettings.JIKES_PATH = myPathField.getText().trim().replace(File.separatorChar, '/');
    myJikesSettings.DEPRECATION = myCbDeprecation.isSelected();
    myJikesSettings.DEBUGGING_INFO = myCbDebuggingInfo.isSelected();
    myJikesSettings.GENERATE_NO_WARNINGS = myCbGenerateNoWarnings.isSelected();
    myJikesSettings.ADDITIONAL_OPTIONS_STRING = myAdditionalOptionsField.getText();
  }

  public void reset() {
    myPathField.setText(myJikesSettings.JIKES_PATH.replace('/', File.separatorChar));
    myCbDeprecation.setSelected(myJikesSettings.DEPRECATION);
    myCbDebuggingInfo.setSelected(myJikesSettings.DEBUGGING_INFO);
    myCbGenerateNoWarnings.setSelected(myJikesSettings.GENERATE_NO_WARNINGS);
    myAdditionalOptionsField.setText(myJikesSettings.ADDITIONAL_OPTIONS_STRING);
  }

  public void disposeUIResources() {
  }
}
