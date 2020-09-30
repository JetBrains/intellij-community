// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.InputRedirectAware;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.macro.MacrosDialog;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class CommonJavaParametersPanel extends CommonProgramParametersPanel {
  private LabeledComponent<RawCommandLineEditor> myVMParametersComponent;
  private ProgramInputRedirectPanel myInputRedirectPanel;

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

    myInputRedirectPanel = new ProgramInputRedirectPanel();
    add(myInputRedirectPanel);
  }

  @Override
  protected final boolean isMacroSupportEnabled() {
    return true;
  }

  @Override
  protected void initMacroSupport() {
    super.initMacroSupport();
    addMacroSupport(myVMParametersComponent.getComponent().getEditorField(), MacrosDialog.Filters.ALL);
    addMacroSupport((ExtendableTextField)myInputRedirectPanel.getComponent().getTextField(), MacrosDialog.Filters.ANY_PATH);
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
    myAnchor = UIUtil.mergeComponentsWithAnchor(this, myVMParametersComponent, myInputRedirectPanel);
  }

  public void applyTo(CommonJavaRunConfigurationParameters configuration) {
    super.applyTo(configuration);
    configuration.setVMParameters(getVMParameters());
    InputRedirectAware.InputRedirectOptions inputRedirectOptions =
      configuration instanceof RunConfiguration ? InputRedirectAware.getInputRedirectOptions((RunConfiguration)configuration) : null;
    if (inputRedirectOptions != null) {
      myInputRedirectPanel.applyTo(inputRedirectOptions);
    }
  }

  public void reset(CommonJavaRunConfigurationParameters configuration) {
    super.reset(configuration);
    setVMParameters(configuration.getVMParameters());
    InputRedirectAware.InputRedirectOptions inputRedirectOptions =
      configuration instanceof RunConfiguration ? InputRedirectAware.getInputRedirectOptions((RunConfiguration)configuration) : null;
    myInputRedirectPanel.setVisible(inputRedirectOptions != null);
    myInputRedirectPanel.reset(inputRedirectOptions);
  }
}
