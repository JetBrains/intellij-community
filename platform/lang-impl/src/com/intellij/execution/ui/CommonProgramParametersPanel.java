/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.execution.ui;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.PathMacros;
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
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class CommonProgramParametersPanel extends JPanel {
  private static final Icon ICON = IconLoader.getIcon("/runConfigurations/variables.png");

  private LabeledComponent<RawCommandLineEditor> myProgramParametersComponent;
  private LabeledComponent<JPanel> myWorkingDirectoryComponent;
  private TextFieldWithBrowseButton myWorkingDirectoryField;
  private EnvironmentVariablesComponent myEnvVariablesComponent;

  private Module myModuleContext = null;

  public CommonProgramParametersPanel() {
    super();
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    
    initComponents();
    copyDialogCaption(myProgramParametersComponent);
  }

  protected void initComponents() {
    myProgramParametersComponent = LabeledComponent.create(new RawCommandLineEditor(),
                                                  ExecutionBundle.message("run.configuration.program.parameters"));

    final JPanel panel = new JPanel(new BorderLayout());
    myWorkingDirectoryField = new TextFieldWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        fileChooserDescriptor.setTitle(ExecutionBundle.message("select.working.directory.message"));
        fileChooserDescriptor.putUserData(LangDataKeys.MODULE_CONTEXT, myModuleContext);
        VirtualFile[] files = FileChooser.chooseFiles(myWorkingDirectoryComponent, fileChooserDescriptor);
        if (files.length != 0) {
          setWorkingDirectory(files[0].getPresentableUrl());
        }
      }
    });
    panel.add(myWorkingDirectoryField, BorderLayout.CENTER);

    final FixedSizeButton button = new FixedSizeButton(myWorkingDirectoryField);
    button.setIcon(ICON);
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final List<String> macros = new ArrayList<String>(PathMacros.getInstance().getUserMacroNames());
        if (myModuleContext != null) macros.add("MODULE_DIR");

        final JList list = new JBList(ArrayUtil.toStringArray(macros));
        final JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list).setItemChoosenCallback(new Runnable() {
          public void run() {
            final Object value = list.getSelectedValue();
            if (value instanceof String) {
              setWorkingDirectory("$" + value + "$");
            }
          }
        }).setMovable(false).setResizable(false).createPopup();
        popup.showUnderneathOf(button);
      }
    });
    panel.add(button, BorderLayout.EAST);

    myWorkingDirectoryComponent = LabeledComponent.create(panel, ExecutionBundle.message("run.configuration.working.directory.label"));
    myEnvVariablesComponent = new EnvironmentVariablesComponent();

    addComponents();

    setPreferredSize(new Dimension(10, 10));
  }

  protected void addComponents() {
    add(myProgramParametersComponent);
    add(myWorkingDirectoryComponent);
    add(myEnvVariablesComponent);
  }

  protected void copyDialogCaption(final LabeledComponent<RawCommandLineEditor> component) {
    final RawCommandLineEditor rawCommandLineEditor = component.getComponent();
    rawCommandLineEditor.setDialogCaption(component.getRawText());
    component.getLabel().setLabelFor(rawCommandLineEditor.getTextField());
  }

  public void setProgramParametersLabel(String textWithMnemonic) {
    myProgramParametersComponent.setText(textWithMnemonic);
    copyDialogCaption(myProgramParametersComponent);
  }

  public String getProgramParametersLabel() {
    return myProgramParametersComponent.getText();
  }

  public void setProgramParameters(String params) {
    myProgramParametersComponent.getComponent().setText(params);
  }

  public void setWorkingDirectory(String dir) {
    myWorkingDirectoryField.setText(dir);
  }

  public void setModuleContext(Module moduleContext) {
    myModuleContext = moduleContext;
  }

  public void applyTo(CommonProgramRunConfigurationParameters configuration) {
    configuration.setProgramParameters(myProgramParametersComponent.getComponent().getText());
    configuration.setWorkingDirectory(myWorkingDirectoryField.getText());

    configuration.setEnvs(myEnvVariablesComponent.getEnvs());
    configuration.setPassParentEnvs(myEnvVariablesComponent.isPassParentEnvs());
  }

  public void reset(CommonProgramRunConfigurationParameters configuration) {
    setProgramParameters(configuration.getProgramParameters());
    setWorkingDirectory(configuration.getWorkingDirectory());

    myEnvVariablesComponent.setEnvs(configuration.getEnvs());
    myEnvVariablesComponent.setPassParentEnvs(configuration.isPassParentEnvs());
  }
}
