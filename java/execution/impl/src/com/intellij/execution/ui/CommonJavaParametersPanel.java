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

import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.InputRedirectAware;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CommonJavaParametersPanel extends CommonProgramParametersPanel {
  private LabeledComponent<RawCommandLineEditor> myVMParametersComponent;
  private RedirectInputPanel myRedirectInputPanel;

  public CommonJavaParametersPanel() {
    super();
  }

  @Override
  protected void addComponents() {
    myVMParametersComponent = LabeledComponent.create(new RawCommandLineEditor(),
                                             ExecutionBundle.message("run.configuration.java.vm.parameters.label"));
    copyDialogCaption(myVMParametersComponent);

    myVMParametersComponent.setLabelLocation(BorderLayout.WEST);

    add(myVMParametersComponent);
    super.addComponents();

    myRedirectInputPanel = new RedirectInputPanel();
    add(myRedirectInputPanel);
  }

  public void setVMParameters(String text) {
    myVMParametersComponent.getComponent().setText(text);
  }

  public String getVMParameters() {
    return myVMParametersComponent.getComponent().getText();
  }

  public LabeledComponent<RawCommandLineEditor> getVMParametersComponent() {
    return myVMParametersComponent;
  }

  @Override
  public void setAnchor(JComponent labelAnchor) {
    super.setAnchor(labelAnchor);
    myVMParametersComponent.setAnchor(labelAnchor);
  }

  @Override
  protected void setupAnchor() {
    super.setupAnchor();
    myAnchor = UIUtil.mergeComponentsWithAnchor(this, myVMParametersComponent, myRedirectInputPanel);
  }

  public void applyTo(CommonJavaRunConfigurationParameters configuration) {
    super.applyTo(configuration);
    configuration.setVMParameters(getVMParameters());
    InputRedirectAware.InputRedirectOptions inputRedirectOptions =
      configuration instanceof RunConfiguration ? InputRedirectAware.getInputRedirectOptions((RunConfiguration)configuration) : null;
    if (inputRedirectOptions != null) {
      inputRedirectOptions.myRedirectInput = myRedirectInputPanel.myCheckBox.isSelected();
      String filePath = myRedirectInputPanel.myInputFile.getText();
      inputRedirectOptions.myInputFile = StringUtil.isEmpty(filePath) ? null : FileUtil.toSystemIndependentName(filePath);
    }
  }

  public void reset(CommonJavaRunConfigurationParameters configuration) {
    super.reset(configuration);
    setVMParameters(configuration.getVMParameters());
    InputRedirectAware.InputRedirectOptions inputRedirectOptions =
      configuration instanceof RunConfiguration ? InputRedirectAware.getInputRedirectOptions((RunConfiguration)configuration) : null;
    if (inputRedirectOptions != null) {
      myRedirectInputPanel.setVisible(true);
      myRedirectInputPanel.myCheckBox.setSelected(inputRedirectOptions.myRedirectInput);
      myRedirectInputPanel.myInputFile.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(inputRedirectOptions.myInputFile)));
      myRedirectInputPanel.myInputFile.setEnabled(inputRedirectOptions.myRedirectInput);
    } else {
      myRedirectInputPanel.setVisible(false);
      myRedirectInputPanel.myCheckBox.setSelected(false);
      myRedirectInputPanel.myInputFile.setText("");
      myRedirectInputPanel.myInputFile.setEnabled(false);
    }
  }

  private static class RedirectInputPanel extends JPanel implements PanelWithAnchor {
    private final JBCheckBox myCheckBox = new JBCheckBox("Redirect input from:");
    private final TextFieldWithBrowseButton myInputFile = new TextFieldWithBrowseButton();

    public RedirectInputPanel() {
      super(new BorderLayout(JBUI.scale(10), JBUI.scale(2)));
       myInputFile.addBrowseFolderListener(null, null, null,
                                           FileChooserDescriptorFactory.createSingleFileDescriptor(),
                                           TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT);
       add(myCheckBox, BorderLayout.WEST);
       add(myInputFile, BorderLayout.CENTER);
       myCheckBox.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
           myInputFile.setEnabled(myCheckBox.isSelected());
         }
       });
       myInputFile.setEnabled(false);
    }

    @Override
    public JComponent getAnchor() {
      return myCheckBox.getAnchor();
    }

    @Override
    public void setAnchor(@Nullable JComponent anchor) {
       myCheckBox.setAnchor(anchor);
    }
  }
}
