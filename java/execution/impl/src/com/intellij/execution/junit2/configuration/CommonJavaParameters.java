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
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class CommonJavaParameters extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.configuration.CommonJavaParameters");

  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/variables.png");

  private static final int[] ourProperties = new int[]{
    RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY,
    RunJavaConfiguration.VM_PARAMETERS_PROPERTY,
    RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY
  };

  private JPanel myWholePanel;
  private LabeledComponent<RawCommandLineEditor> myProgramParameters;
  private LabeledComponent<RawCommandLineEditor> myVMParameters;
  private LabeledComponent<JPanel> myWorkingDirectoryComponent;

  private final LabeledComponent[] myFields = new LabeledComponent[3];
  private Module myModule = null;
  private JButton myVariablesButton;
  private TextFieldWithBrowseButton myWorkingDirectoryField;

  public CommonJavaParameters() {
    super(new BorderLayout());
    add(myWholePanel, BorderLayout.CENTER);
    copyDialogCaption(myProgramParameters);
    copyDialogCaption(myVMParameters);

    myFields[RunJavaConfiguration.PROGRAM_PARAMETERS_PROPERTY] = myProgramParameters;
    myFields[RunJavaConfiguration.VM_PARAMETERS_PROPERTY] = myVMParameters;
    myFields[RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY] = myWorkingDirectoryComponent;
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
    else if (component instanceof JPanel)
      myWorkingDirectoryField.setText(value);
    else LOG.error(component.getClass().getName());
  }

  public String getText(final int property) {
    final JComponent component = getLabeledComponent(property).getComponent();
    if (component instanceof TextFieldWithBrowseButton)
      return ((TextFieldWithBrowseButton)component).getText();
    else if (component instanceof RawCommandLineEditor)
      return ((RawCommandLineEditor)component).getText();
    else if (component instanceof JPanel)
      return myWorkingDirectoryField.getText();
    else LOG.error(component.getClass().getName());
    return "";
  }

  private LabeledComponent getLabeledComponent(final int index) {
    return myFields[index];
  }

  public void setModuleContext(final Module module) {
    myModule = module;
  }

  private void createUIComponents() {
    final JPanel panel = new JPanel(new BorderLayout());
    myWorkingDirectoryField = new TextFieldWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        fileChooserDescriptor.setTitle(ExecutionBundle.message("select.working.directory.message"));
        fileChooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, myModule);
        VirtualFile[] files = FileChooser.chooseFiles(myWorkingDirectoryComponent, fileChooserDescriptor);
        if (files.length != 0) {
          setText(RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY, files[0].getPresentableUrl());
        }
      }
    });
    panel.add(myWorkingDirectoryField, BorderLayout.CENTER);

    final FixedSizeButton button = new FixedSizeButton(myWorkingDirectoryField);
    button.setIcon(ICON);
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final List<String> macros = new ArrayList<String>(PathMacros.getInstance().getUserMacroNames());
        macros.add("MODULE_DIR");

        final JList list = new JList(ArrayUtil.toStringArray(macros));
        final JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list).setItemChoosenCallback(new Runnable() {
          public void run() {
            final Object value = list.getSelectedValue();
            if (value instanceof String) {
              setText(RunJavaConfiguration.WORKING_DIRECTORY_PROPERTY, "$" + value + "$");
            }
          }
        }).setMovable(false).setResizable(false).createPopup();
        popup.showUnderneathOf(button);
      }
    });
    panel.add(button, BorderLayout.EAST);

    myWorkingDirectoryComponent = LabeledComponent.create(panel, "&Working directory");
  }
}
