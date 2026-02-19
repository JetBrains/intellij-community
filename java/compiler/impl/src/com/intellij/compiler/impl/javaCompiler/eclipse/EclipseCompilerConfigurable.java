// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.compiler.options.ComparingUtils;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.jps.model.java.compiler.EclipseCompilerOptions;

import javax.swing.JComponent;

public class EclipseCompilerConfigurable implements Configurable {
  private final Project myProject;
  private EclipseCompilerConfigurableUi myUi;
  private final EclipseCompilerOptions myCompilerSettings;

  public EclipseCompilerConfigurable(Project project, EclipseCompilerOptions options) {
    myProject = project;
    myCompilerSettings = options;
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    myUi = new EclipseCompilerConfigurableUi(myProject);
    return myUi.getPanel();
  }

  @Override
  public boolean isModified() {
    boolean isModified = false;

    isModified |= ComparingUtils.isModified(myUi.deprecationCb, myCompilerSettings.DEPRECATION);
    isModified |= ComparingUtils.isModified(myUi.debuggingInfoCb, myCompilerSettings.DEBUGGING_INFO);
    isModified |= ComparingUtils.isModified(myUi.generateNoWarningsCb, myCompilerSettings.GENERATE_NO_WARNINGS);
    isModified |= ComparingUtils.isModified(myUi.proceedOnErrorsCb, myCompilerSettings.PROCEED_ON_ERROR);
    isModified |= ComparingUtils.isModified(myUi.pathToEcjField, FileUtil.toSystemDependentName(myCompilerSettings.ECJ_TOOL_PATH));
    isModified |= ComparingUtils.isModified(myUi.additionalOptionsField, myCompilerSettings.ADDITIONAL_OPTIONS_STRING);
    isModified |= !myUi.optionsOverrideComponent.getModuleOptionsMap().equals(myCompilerSettings.ADDITIONAL_OPTIONS_OVERRIDE);

    return isModified;
  }

  @Override
  public void apply() throws ConfigurationException {
    myCompilerSettings.DEPRECATION =  myUi.deprecationCb.isSelected();
    myCompilerSettings.DEBUGGING_INFO = myUi.debuggingInfoCb.isSelected();
    myCompilerSettings.GENERATE_NO_WARNINGS = myUi.generateNoWarningsCb.isSelected();
    myCompilerSettings.PROCEED_ON_ERROR = myUi.proceedOnErrorsCb.isSelected();
    myCompilerSettings.ECJ_TOOL_PATH = FileUtil.toSystemIndependentName(myUi.pathToEcjField.getText().trim());
    myCompilerSettings.ADDITIONAL_OPTIONS_STRING = myUi.additionalOptionsField.getText();
    myCompilerSettings.ADDITIONAL_OPTIONS_OVERRIDE.clear();
    myCompilerSettings.ADDITIONAL_OPTIONS_OVERRIDE.putAll(myUi.optionsOverrideComponent.getModuleOptionsMap());
  }

  @Override
  public void reset() {
    myUi.deprecationCb.setSelected(myCompilerSettings.DEPRECATION);
    myUi.debuggingInfoCb.setSelected(myCompilerSettings.DEBUGGING_INFO);
    myUi.generateNoWarningsCb.setSelected(myCompilerSettings.GENERATE_NO_WARNINGS);
    myUi.proceedOnErrorsCb.setSelected(myCompilerSettings.PROCEED_ON_ERROR);
    myUi.pathToEcjField.setText(FileUtil.toSystemDependentName(myCompilerSettings.ECJ_TOOL_PATH));
    myUi.additionalOptionsField.setText(myCompilerSettings.ADDITIONAL_OPTIONS_STRING);
    myUi.optionsOverrideComponent.setModuleOptionsMap(myCompilerSettings.ADDITIONAL_OPTIONS_OVERRIDE);
  }

  @Override
  public void disposeUIResources() {
    myUi = null;
  }
}
