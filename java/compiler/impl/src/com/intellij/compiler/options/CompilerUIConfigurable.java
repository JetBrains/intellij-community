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

import com.intellij.compiler.*;
import com.intellij.compiler.impl.TranslatingCompilerFilesMonitor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class CompilerUIConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private JPanel myPanel;
  private final Project myProject;

  private JTextField myResourcePatternsField;
  private JCheckBox myCbCompileInBackground;
  private JCheckBox myCbClearOutputDirectory;
  private JCheckBox myCbAssertNotNull;
  private JLabel myPatternLegendLabel;
  private JCheckBox myCbAutoShowFirstError;
  private JCheckBox myCbUseCompileServer;
  private JCheckBox myCbMakeProjectOnSave;

  public CompilerUIConfigurable(final Project project) {
    myProject = project;
    final boolean isServerOptionEnabled = Registry.is("compiler.server.enabled") || ApplicationManager.getApplication().isInternal();
    myCbUseCompileServer.setVisible(isServerOptionEnabled);
    myCbMakeProjectOnSave.setVisible(isServerOptionEnabled);

    myPatternLegendLabel.setText("<html>" +
                                 "Use <b>;</b> to separate patterns and <b>!</b> to negate a pattern. " +
                                 "Accepted wildcards: <b>?</b> &mdash; exactly one symbol; <b>*</b> &mdash; zero or more symbols; " +
                                 "<b>/</b> &mdash; path separator; <b>/**/</b> &mdash; any number of directories; " +
                                 "<i>&lt;dir_name&gt;</i>:<i>&lt;pattern&gt;</i> &mdash; restrict to source roots with the specified name" +
                                 "</html>");
    myCbUseCompileServer.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myCbMakeProjectOnSave.setEnabled(myCbUseCompileServer.isSelected());
      }
    });
  }

  public void reset() {

    final CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    myCbCompileInBackground.setSelected(workspaceConfiguration.COMPILE_IN_BACKGROUND);
    myCbAutoShowFirstError.setSelected(workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR);
    myCbClearOutputDirectory.setSelected(workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY);
    myCbAssertNotNull.setSelected(workspaceConfiguration.ASSERT_NOT_NULL);
    myCbUseCompileServer.setSelected(workspaceConfiguration.USE_COMPILE_SERVER);
    myCbMakeProjectOnSave.setSelected(workspaceConfiguration.MAKE_PROJECT_ON_SAVE);
    myCbMakeProjectOnSave.setEnabled(workspaceConfiguration.USE_COMPILE_SERVER);

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
    workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR = myCbAutoShowFirstError.isSelected();
    workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY = myCbClearOutputDirectory.isSelected();
    workspaceConfiguration.ASSERT_NOT_NULL = myCbAssertNotNull.isSelected();
    workspaceConfiguration.USE_COMPILE_SERVER = myCbUseCompileServer.isSelected();
    workspaceConfiguration.MAKE_PROJECT_ON_SAVE = myCbMakeProjectOnSave.isSelected();

    configuration.removeResourceFilePatterns();
    String extensionString = myResourcePatternsField.getText().trim();
    applyResourcePatterns(extensionString, (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject));

    // this will schedule for compilation all files that might become compilable after resource patterns' changing
    final TranslatingCompilerFilesMonitor monitor = TranslatingCompilerFilesMonitor.getInstance();
    if (!workspaceConfiguration.USE_COMPILE_SERVER) {
      CompileServerManager.getInstance().shutdownServer();
      monitor.watchProject(myProject);
      monitor.scanSourcesForCompilableFiles(myProject);
    }
    else {
      monitor.suspendProject(myProject);
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          CompileServerManager.getInstance().sendReloadRequest(myProject);
        }
      });
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
    isModified |= ComparingUtils.isModified(myCbAutoShowFirstError, workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR);
    isModified |= ComparingUtils.isModified(myCbAssertNotNull, workspaceConfiguration.ASSERT_NOT_NULL);
    isModified |= ComparingUtils.isModified(myCbUseCompileServer, workspaceConfiguration.USE_COMPILE_SERVER);
    isModified |= ComparingUtils.isModified(myCbMakeProjectOnSave, workspaceConfiguration.MAKE_PROJECT_ON_SAVE);

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
}
