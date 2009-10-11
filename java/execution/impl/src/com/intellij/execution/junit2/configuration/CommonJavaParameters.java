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
package com.intellij.execution.junit2.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunJavaConfiguration;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CommonJavaParameters extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.configuration.CommonJavaParameters");
  private static final int[] ourProperties = new int[]{
    RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY,
    RunJavaConfiguration.VM_PARAMETERS_PROPERTY,
    RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY
  };

  private JPanel myWholePanel;
  private LabeledComponent<TextFieldWithBrowseButton> myWorkingDirectory;
  private LabeledComponent<RawCommandLineEditor> myProgramParameters;
  private LabeledComponent<RawCommandLineEditor> myVMParameters;

  private final LabeledComponent[] myFields = new LabeledComponent[3];
  private Module myModule = null;

  public CommonJavaParameters() {
    super(new BorderLayout());
    add(myWholePanel, BorderLayout.CENTER);
    copyDialogCaption(myProgramParameters);
    copyDialogCaption(myVMParameters);
    myWorkingDirectory.getComponent()
      .addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        fileChooserDescriptor.setTitle(ExecutionBundle.message("select.working.directory.message"));
        fileChooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, myModule);
        VirtualFile[] files = FileChooser.chooseFiles(myWorkingDirectory, fileChooserDescriptor);
        if (files.length != 0) {
          setText(RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY, files[0].getPresentableUrl());
        }
      }
    });
    myFields[RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY] = myProgramParameters;
    myFields[RunJavaConfiguration.VM_PARAMETERS_PROPERTY] = myVMParameters;
    myFields[RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY] = myWorkingDirectory;
  }

  private static void copyDialogCaption(final LabeledComponent<RawCommandLineEditor> component) {
    final RawCommandLineEditor rawCommandLineEditor = component.getComponent();
    rawCommandLineEditor.setDialogCaption(component.getRawText());
    component.getLabel().setLabelFor(rawCommandLineEditor.getTextField());
  }

  public String getProgramParametersText() {
    return getLabeledComponent(RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY).getText();
  }

  public void setProgramParametersText(String textWithMnemonic) {
    getLabeledComponent(RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY).setText(textWithMnemonic);
    copyDialogCaption(myProgramParameters);
  }

  public void applyTo(final RunJavaConfiguration configuration) {
    for (final int property : ourProperties) {
      configuration.setProperty(property, getText(property));
    }
  }

  public void reset(final RunJavaConfiguration configuration) {
    for (final int property : ourProperties) {
      setText(property, configuration.getProperty(property));
    }
  }

  public void setText(final int property, final String value) {
    final JComponent component = getLabeledComponent(property).getComponent();
    if (component instanceof TextFieldWithBrowseButton)
      ((TextFieldWithBrowseButton)component).setText(value);
    else if (component instanceof RawCommandLineEditor)
      ((RawCommandLineEditor)component).setText(value);
    else LOG.error(component.getClass().getName());
  }

  public String getText(final int property) {
    final JComponent component = getLabeledComponent(property).getComponent();
    if (component instanceof TextFieldWithBrowseButton)
      return ((TextFieldWithBrowseButton)component).getText();
    else if (component instanceof RawCommandLineEditor)
      return ((RawCommandLineEditor)component).getText();
    else LOG.error(component.getClass().getName());
    return "";
  }

  private LabeledComponent getLabeledComponent(final int index) {
    return myFields[index];
  }

  public void setModuleContext(final Module module) {
    myModule = module;
  }
}
