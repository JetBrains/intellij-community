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
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class CompilerUIConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.options.CompilerUIConfigurable");
  private JPanel myPanel;
  private final Project myProject;

  private JTextField myResourcePatternsField;
  private JCheckBox myCbClearOutputDirectory;
  private JCheckBox myCbAssertNotNull;
  private JBLabel myPatternLegendLabel;
  private JCheckBox myCbAutoShowFirstError;
  private JCheckBox myCbUseExternalBuild;
  private JCheckBox myCbEnableAutomake;
  private JCheckBox myCbParallelCompilation;
  private JTextField myHeapSizeField;
  private JTextField myVMOptionsField;
  private JLabel myHeapSizeLabel;
  private JLabel myVMOptionsLabel;

  public CompilerUIConfigurable(final Project project) {
    myProject = project;

    myPatternLegendLabel.setText("<html>" +
                                 "Use <b>;</b> to separate patterns and <b>!</b> to negate a pattern. " +
                                 "Accepted wildcards: <b>?</b> &mdash; exactly one symbol; <b>*</b> &mdash; zero or more symbols; " +
                                 "<b>/</b> &mdash; path separator; <b>/**/</b> &mdash; any number of directories; " +
                                 "<i>&lt;dir_name&gt;</i>:<i>&lt;pattern&gt;</i> &mdash; restrict to source roots with the specified name" +
                                 "</html>");
    myCbUseExternalBuild.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateExternalMakeOptionControls(myCbUseExternalBuild.isSelected());
      }
    });
  }

  public void reset() {

    final CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    myCbAutoShowFirstError.setSelected(workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR);
    myCbClearOutputDirectory.setSelected(workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY);
    myCbAssertNotNull.setSelected(configuration.isAddNotNullAssertions());
    myCbUseExternalBuild.setSelected(workspaceConfiguration.USE_COMPILE_SERVER);
    myCbEnableAutomake.setSelected(workspaceConfiguration.MAKE_PROJECT_ON_SAVE);
    myCbParallelCompilation.setSelected(workspaceConfiguration.PARALLEL_COMPILATION);
    myHeapSizeField.setText(String.valueOf(workspaceConfiguration.COMPILER_PROCESS_HEAP_SIZE));
    final String options = workspaceConfiguration.COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS;
    myVMOptionsField.setText(options == null? "" : options.trim());
    updateExternalMakeOptionControls(myCbUseExternalBuild.isSelected());

    configuration.convertPatterns();

    myResourcePatternsField.setText(patternsToString(configuration.getResourceFilePatterns()));
  }

  private static String patternsToString(final String[] patterns) {
    final StringBuilder extensionsString = new StringBuilder();
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
    workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR = myCbAutoShowFirstError.isSelected();
    workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY = myCbClearOutputDirectory.isSelected();
    boolean wasUsingExternalMake = workspaceConfiguration.USE_COMPILE_SERVER;
    workspaceConfiguration.USE_COMPILE_SERVER = myCbUseExternalBuild.isSelected();
    workspaceConfiguration.MAKE_PROJECT_ON_SAVE = myCbEnableAutomake.isSelected();
    workspaceConfiguration.PARALLEL_COMPILATION = myCbParallelCompilation.isSelected();
    try {
      workspaceConfiguration.COMPILER_PROCESS_HEAP_SIZE = Integer.parseInt(myHeapSizeField.getText().trim());
    }
    catch (NumberFormatException ignored) {
      LOG.info(ignored);
    }
    workspaceConfiguration.COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS = myVMOptionsField.getText().trim();

    configuration.setAddNotNullAssertions(myCbAssertNotNull.isSelected());
    configuration.removeResourceFilePatterns();
    String extensionString = myResourcePatternsField.getText().trim();
    applyResourcePatterns(extensionString, (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject));
    if (wasUsingExternalMake != workspaceConfiguration.USE_COMPILE_SERVER) {
      myProject.getMessageBus().syncPublisher(ExternalBuildOptionListener.TOPIC).externalBuildOptionChanged(workspaceConfiguration.USE_COMPILE_SERVER);
    }
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
      final StringBuilder pattersnsWithErrors = new StringBuilder();
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
    isModified |= ComparingUtils.isModified(myCbAutoShowFirstError, workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR);
    isModified |= ComparingUtils.isModified(myCbUseExternalBuild, workspaceConfiguration.USE_COMPILE_SERVER);
    isModified |= ComparingUtils.isModified(myCbEnableAutomake, workspaceConfiguration.MAKE_PROJECT_ON_SAVE);
    isModified |= ComparingUtils.isModified(myCbParallelCompilation, workspaceConfiguration.PARALLEL_COMPILATION);
    isModified |= ComparingUtils.isModified(myHeapSizeField, workspaceConfiguration.COMPILER_PROCESS_HEAP_SIZE);
    isModified |= ComparingUtils.isModified(myVMOptionsField, workspaceConfiguration.COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS);

    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    isModified |= ComparingUtils.isModified(myCbAssertNotNull, compilerConfiguration.isAddNotNullAssertions());
    isModified |= ComparingUtils.isModified(myCbClearOutputDirectory, workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY);
    isModified |= ComparingUtils.isModified(myResourcePatternsField, patternsToString(compilerConfiguration.getResourceFilePatterns()));

    return isModified;
  }

  public String getDisplayName() {
    return "General";
  }

  public String getHelpTopic() {
    return null;
  }

  @NotNull
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

  private void updateExternalMakeOptionControls(boolean enabled) {
    myCbEnableAutomake.setEnabled(enabled);
    myCbParallelCompilation.setEnabled(enabled);
    myHeapSizeField.setEnabled(enabled);
    myVMOptionsField.setEnabled(enabled);
    myHeapSizeLabel.setEnabled(enabled);
    myVMOptionsLabel.setEnabled(enabled);
  }

}
