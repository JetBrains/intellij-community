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
package com.intellij.compiler.options;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.MalformedPatternException;
import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class CompilerUIConfigurable implements SearchableConfigurable {
  private JPanel myPanel;
  private final Project myProject;

  private JTextField myResourcePatternsField;
  private JCheckBox myCbCompileInBackground;
  private JCheckBox myCbClearOutputDirectory;
  private JCheckBox myCbCompileDependent;
  private JCheckBox myCbAssertNotNull;
  private JLabel myPatternLegendLabel;

  public CompilerUIConfigurable(final Project project) {
    myProject = project;
  }

  public void reset() {

    final CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    myCbCompileInBackground.setSelected(workspaceConfiguration.COMPILE_IN_BACKGROUND);
    myCbCompileDependent.setSelected(workspaceConfiguration.COMPILE_DEPENDENT_FILES);
    myCbClearOutputDirectory.setSelected(workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY);
    myCbAssertNotNull.setSelected(workspaceConfiguration.ASSERT_NOT_NULL);

    configuration.convertPatterns();

    myResourcePatternsField.setText(patternsToString(configuration.getResourceFilePatterns()));
  }

  private static String patternsToString(final String[] patterns) {
    final StringBuffer extensionsString = new StringBuffer();
    for (int idx = 0; idx < patterns.length; idx++) {
      if (idx > 0) {
        extensionsString.append(";");
      }
      extensionsString.append(patterns[idx]);
    }
    return extensionsString.toString();
  }

  public void apply() throws ConfigurationException {

    CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    workspaceConfiguration.COMPILE_IN_BACKGROUND = myCbCompileInBackground.isSelected();
    workspaceConfiguration.COMPILE_DEPENDENT_FILES = myCbCompileDependent.isSelected();
    workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY = myCbClearOutputDirectory.isSelected();
    workspaceConfiguration.ASSERT_NOT_NULL = myCbAssertNotNull.isSelected();

    configuration.removeResourceFilePatterns();
    String extensionString = myResourcePatternsField.getText().trim();
    applyResourcePatterns(extensionString, (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject));

    // this will schedule for compilation all files that might become compilable after resource patterns' changing
    TranslatingCompilerFilesMonitor.getInstance().scanSourcesForCompilableFiles(myProject);
  }

  private static void applyResourcePatterns(String extensionString, final CompilerConfigurationImpl configuration)
    throws ConfigurationException {
    StringTokenizer tokenizer = new StringTokenizer(extensionString, ";", false);
    List<String[]> errors = new ArrayList<String[]>();

    while (tokenizer.hasMoreTokens()) {
      String namePattern = tokenizer.nextToken();
      try {
        configuration.addResourceFilePattern(namePattern);
      }
      catch (MalformedPatternException e) {
        errors.add(new String[]{namePattern, e.getLocalizedMessage()});
      }
    }

    if (errors.size() > 0) {
      final StringBuffer pattersnsWithErrors = new StringBuffer();
      for (final Object error : errors) {
        String[] pair = (String[])error;
        pattersnsWithErrors.append("\n");
        pattersnsWithErrors.append(pair[0]);
        pattersnsWithErrors.append(": ");
        pattersnsWithErrors.append(pair[1]);
      }

      throw new ConfigurationException(
        CompilerBundle.message("error.compiler.configurable.malformed.patterns", pattersnsWithErrors.toString()), CompilerBundle.message("bad.resource.patterns.dialog.title")
      );
    }
  }

  public boolean isModified() {
    boolean isModified = false;
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    isModified |= ComparingUtils.isModified(myCbCompileInBackground, workspaceConfiguration.COMPILE_IN_BACKGROUND);
    isModified |= ComparingUtils.isModified(myCbCompileDependent, workspaceConfiguration.COMPILE_DEPENDENT_FILES);
    isModified |= ComparingUtils.isModified(myCbAssertNotNull, workspaceConfiguration.ASSERT_NOT_NULL);

    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    isModified |= ComparingUtils.isModified(myCbClearOutputDirectory, workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY);
    isModified |= ComparingUtils.isModified(myResourcePatternsField, patternsToString(compilerConfiguration.getResourceFilePatterns()));

    return isModified;
  }

  public String getDisplayName() {
    return "General";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public String getId() {
    return "compiler.general";
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public void disposeUIResources() {
  }
}
