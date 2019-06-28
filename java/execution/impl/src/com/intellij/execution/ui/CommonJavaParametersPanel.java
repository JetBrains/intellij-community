// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.InputRedirectAware;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.util.ProgramParametersConfigurator;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.scale.JBUIScale;
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
    ProgramParametersConfigurator.addMacroSupport(myVMParametersComponent.getComponent().getEditorField());

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
      inputRedirectOptions.setRedirectInput(myRedirectInputPanel.myCheckBox.isSelected());
      String filePath = myRedirectInputPanel.myInputFile.getText();
      inputRedirectOptions.setRedirectInputPath(StringUtil.isEmpty(filePath) ? null : FileUtil.toSystemIndependentName(filePath));
    }
  }

  public void reset(CommonJavaRunConfigurationParameters configuration) {
    super.reset(configuration);
    setVMParameters(configuration.getVMParameters());
    InputRedirectAware.InputRedirectOptions inputRedirectOptions =
      configuration instanceof RunConfiguration ? InputRedirectAware.getInputRedirectOptions((RunConfiguration)configuration) : null;
    if (inputRedirectOptions != null) {
      myRedirectInputPanel.setVisible(true);
      myRedirectInputPanel.myCheckBox.setSelected(inputRedirectOptions.isRedirectInput());
      myRedirectInputPanel.myInputFile.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(inputRedirectOptions.getRedirectInputPath())));
      myRedirectInputPanel.myInputFile.setEnabled(inputRedirectOptions.isRedirectInput());
    }
    else {
      myRedirectInputPanel.setVisible(false);
      myRedirectInputPanel.myCheckBox.setSelected(false);
      myRedirectInputPanel.myInputFile.setText("");
      myRedirectInputPanel.myInputFile.setEnabled(false);
    }
  }

  private static class RedirectInputPanel extends JPanel implements PanelWithAnchor {
    private final JBCheckBox myCheckBox = new JBCheckBox("Redirect input from:");
    private final TextFieldWithBrowseButton myInputFile = new TextFieldWithBrowseButton();

    RedirectInputPanel() {
      super(new BorderLayout(JBUIScale.scale(10), JBUIScale.scale(2)));
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
