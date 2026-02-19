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
package com.intellij.compiler.impl.javaCompiler.javac;

import com.intellij.compiler.options.ComparingUtils;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions;

import javax.swing.JComponent;

/**
 * @author Eugene Zhuravlev
 */
public class JavacConfigurable implements Configurable {
  private final Project myProject;
  private JavacConfigurableUi myUi;
  private final JpsJavaCompilerOptions myJavacSettings;

  public JavacConfigurable(Project project, final JpsJavaCompilerOptions javacSettings) {
    myJavacSettings = javacSettings;
    myProject = project;
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  @Override
  public JComponent createComponent() {
    myUi = new JavacConfigurableUi(myProject);
    return myUi.getPanel();
  }

  @Override
  public boolean isModified() {
    boolean isModified = false;
    isModified |= ComparingUtils.isModified(myUi.preferTargetJdkCompilerCb, myJavacSettings.PREFER_TARGET_JDK_COMPILER);
    isModified |= ComparingUtils.isModified(myUi.deprecationCb, myJavacSettings.DEPRECATION);
    isModified |= ComparingUtils.isModified(myUi.debuggingInfoCb, myJavacSettings.DEBUGGING_INFO);
    isModified |= ComparingUtils.isModified(myUi.generateNoWarningsCb, myJavacSettings.GENERATE_NO_WARNINGS);
    isModified |= ComparingUtils.isModified(myUi.additionalOptionsField, myJavacSettings.ADDITIONAL_OPTIONS_STRING);
    isModified |= !myUi.optionsOverrideComponent.getModuleOptionsMap().equals(myJavacSettings.ADDITIONAL_OPTIONS_OVERRIDE);

    return isModified;
  }

  @Override
  public void apply() throws ConfigurationException {
    myJavacSettings.PREFER_TARGET_JDK_COMPILER =  myUi.preferTargetJdkCompilerCb.isSelected();
    myJavacSettings.DEPRECATION =  myUi.deprecationCb.isSelected();
    myJavacSettings.DEBUGGING_INFO = myUi.debuggingInfoCb.isSelected();
    myJavacSettings.GENERATE_NO_WARNINGS = myUi.generateNoWarningsCb.isSelected();
    myJavacSettings.ADDITIONAL_OPTIONS_STRING = myUi.additionalOptionsField.getText();
    myJavacSettings.ADDITIONAL_OPTIONS_OVERRIDE.clear();
    myJavacSettings.ADDITIONAL_OPTIONS_OVERRIDE.putAll(myUi.optionsOverrideComponent.getModuleOptionsMap());
  }

  @Override
  public void reset() {
    myUi.preferTargetJdkCompilerCb.setSelected(myJavacSettings.PREFER_TARGET_JDK_COMPILER);
    myUi.deprecationCb.setSelected(myJavacSettings.DEPRECATION);
    myUi.debuggingInfoCb.setSelected(myJavacSettings.DEBUGGING_INFO);
    myUi.generateNoWarningsCb.setSelected(myJavacSettings.GENERATE_NO_WARNINGS);
    myUi.additionalOptionsField.setText(myJavacSettings.ADDITIONAL_OPTIONS_STRING);
    myUi.optionsOverrideComponent.setModuleOptionsMap(myJavacSettings.ADDITIONAL_OPTIONS_OVERRIDE);
  }

  @Override
  public void disposeUIResources() {
    myUi = null;
  }
}
