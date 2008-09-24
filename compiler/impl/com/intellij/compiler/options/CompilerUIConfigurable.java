package com.intellij.compiler.options;

import com.intellij.compiler.*;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.compiler.options.ExcludedEntriesConfigurable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.util.Options;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class CompilerUIConfigurable implements Configurable {
  private JPanel myPanel;
  private JPanel myExcludeTablePanel;
  private Project myProject;
  private ExcludedEntriesConfigurable myExcludedEntriesConfigurable;

  private JTextField myResourcePatternsField;
  private JCheckBox myCbCompileInBackground;
  private JCheckBox myCbClearOutputDirectory;
  private JPanel myTabbedPanePanel;
  private JCheckBox myCbCompileDependent;
  private JRadioButton myDoNotDeploy;
  private JRadioButton myDeploy;
  private JRadioButton myShowDialog;
  private JCheckBox myCbAssertNotNull;
  private List<Configurable> myCompilerConfigurables;

  public CompilerUIConfigurable(final Project project) {
    myProject = project;

    CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
    final FileChooserDescriptor descriptor = new FileChooserDescriptor(true, true, false, false, false, true);

    myExcludedEntriesConfigurable = new ExcludedEntriesConfigurable(project, descriptor, compilerConfiguration.getExcludedEntriesConfiguration()) {
      public void apply() {
        super.apply();
        FileStatusManager.getInstance(myProject).fileStatusesChanged(); // refresh exclude from compile status
        //ProjectView.getInstance(myProject).refresh();
      }
    };
    final JComponent exludedPanel = myExcludedEntriesConfigurable.createComponent();
    exludedPanel.setBorder(BorderFactory.createCompoundBorder(
      IdeBorderFactory.createTitledBorder(CompilerBundle.message("label.group.exclude.from.compile")), BorderFactory.createEmptyBorder(2, 2, 2, 2))
    );
    myExcludeTablePanel.setLayout(new BorderLayout());
    myExcludeTablePanel.add(exludedPanel, BorderLayout.CENTER);

    myTabbedPanePanel.setLayout(new BorderLayout());
    final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper();

    myCompilerConfigurables = new ArrayList<Configurable>();

    final CompilerSettingsFactory[] factories = Extensions.getExtensions(CompilerSettingsFactory.EP_NAME, project);
    if (factories.length > 0) {
      for (CompilerSettingsFactory factory : factories) {
        myCompilerConfigurables.add(factory.create(project));
      }
      Collections.sort(myCompilerConfigurables, new Comparator<Configurable>() {
        public int compare(final Configurable o1, final Configurable o2) {
          return Comparing.compare(o1.getDisplayName(), o2.getDisplayName());
        }
      });
    }

    myCompilerConfigurables.add(0, new RmicConfigurable(RmicSettings.getInstance(project)));
    myCompilerConfigurables.add(0, new JavaCompilersTab(project, compilerConfiguration.getRegisteredJavaCompilers(), compilerConfiguration.getDefaultCompiler()));
    for (Configurable configurable : myCompilerConfigurables) {
      tabbedPane.addTab(configurable.getDisplayName(), configurable.createComponent());
    }

    myTabbedPanePanel.add(tabbedPane.getComponent(), BorderLayout.CENTER);

    ButtonGroup deployGroup = new ButtonGroup();
    deployGroup.add(myShowDialog);
    deployGroup.add(myDeploy);
    deployGroup.add(myDoNotDeploy);
  }

  public void reset() {
    myExcludedEntriesConfigurable.reset();

    for (Configurable configurable : myCompilerConfigurables) {
      configurable.reset();
    }

    final CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    myCbCompileInBackground.setSelected(workspaceConfiguration.COMPILE_IN_BACKGROUND);
    myCbCompileDependent.setSelected(workspaceConfiguration.COMPILE_DEPENDENT_FILES);
    myCbClearOutputDirectory.setSelected(workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY);
    myCbAssertNotNull.setSelected(workspaceConfiguration.ASSERT_NOT_NULL);

    configuration.convertPatterns();

    myResourcePatternsField.setText(patternsToString(configuration.getResourceFilePatterns()));

    if (configuration.DEPLOY_AFTER_MAKE == Options.SHOW_DIALOG) {
      myShowDialog.setSelected(true);
    }
    else if (configuration.DEPLOY_AFTER_MAKE == Options.PERFORM_ACTION_AUTOMATICALLY) {
      myDeploy.setSelected(true);
    }
    else {
      myDoNotDeploy.setSelected(true);
    }
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
    myExcludedEntriesConfigurable.apply();

    for (Configurable configurable : myCompilerConfigurables) {
      configurable.apply();
    }

    CompilerConfigurationImpl configuration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    workspaceConfiguration.COMPILE_IN_BACKGROUND = myCbCompileInBackground.isSelected();
    workspaceConfiguration.COMPILE_DEPENDENT_FILES = myCbCompileDependent.isSelected();
    workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY = myCbClearOutputDirectory.isSelected();
    workspaceConfiguration.ASSERT_NOT_NULL = myCbAssertNotNull.isSelected();

    configuration.removeResourceFilePatterns();
    String extensionString = myResourcePatternsField.getText().trim();
    applyResourcePatterns(extensionString, (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject));

    configuration.DEPLOY_AFTER_MAKE = getSelectedDeploymentOption();
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
    if (myExcludedEntriesConfigurable.isModified()) {
      return true;
    }

    boolean isModified = false;
    for (Configurable configurable : myCompilerConfigurables) {
      isModified |= configurable.isModified();
    }

    final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
    isModified |= ComparingUtils.isModified(myCbCompileInBackground, workspaceConfiguration.COMPILE_IN_BACKGROUND);
    isModified |= ComparingUtils.isModified(myCbCompileDependent, workspaceConfiguration.COMPILE_DEPENDENT_FILES);
    isModified |= ComparingUtils.isModified(myCbAssertNotNull, workspaceConfiguration.ASSERT_NOT_NULL);

    final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(myProject);
    isModified |= ComparingUtils.isModified(myCbClearOutputDirectory, workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY);
    isModified |= ComparingUtils.isModified(myResourcePatternsField, patternsToString(compilerConfiguration.getResourceFilePatterns()));
    isModified |= compilerConfiguration.DEPLOY_AFTER_MAKE != getSelectedDeploymentOption();

    return isModified;
  }

  private int getSelectedDeploymentOption() {
    if (myShowDialog.isSelected()) return Options.SHOW_DIALOG;
    if (myDeploy.isSelected()) return Options.PERFORM_ACTION_AUTOMATICALLY;
    return Options.DO_NOTHING;
  }

  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public void disposeUIResources() {
  }
}