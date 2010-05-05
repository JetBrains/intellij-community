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
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.RawCommandLineEditor;

import java.awt.*;

public class CommonJavaParametersPanel extends CommonProgramParametersPanel {
  private LabeledComponent<RawCommandLineEditor> myVMParameters;

  public CommonJavaParametersPanel() {
    super();
  }

  @Override
  protected void addComponents(GridBagConstraints c) {
    myVMParameters = LabeledComponent.create(new RawCommandLineEditor(),
                                             ExecutionBundle.message("run.configuration.java.vm.parameters.label"));
    copyDialogCaption(myVMParameters);


    c.gridy++;
    add(myVMParameters, c);

    super.addComponents(c);
  }

  public void setVMParameters(String text) {
    myVMParameters.getComponent().setText(text);
  }

  public String getVMParameters() {
    return myVMParameters.getComponent().getText();
  }

  public void applyTo(CommonJavaRunConfigurationParameters configuration) {
    super.applyTo(configuration);
    configuration.setVMParameters(getVMParameters());
  }

  public void reset(CommonJavaRunConfigurationParameters configuration) {
    super.reset(configuration);
    setVMParameters(configuration.getVMParameters());
  }
}
