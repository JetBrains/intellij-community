package com.intellij.execution.impl;

import com.intellij.execution.RunManagerConfig;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * User: anna
 * Date: 27-Mar-2006
 */
public class ConfigurationSettingsEditorWrapper extends SettingsEditor<RunnerAndConfigurationSettingsImpl> {

  private JPanel myComponentPlace;
  private JCheckBox myCbStoreProjectConfiguration;
  private JComboBox myCompileMethod;
  private JPanel myWholePanel;
  private ConfigurationSettingsEditor myEditor;
  private String myCompileBeforeRunning;
  private boolean myStoreProjectConfiguration;

  public ConfigurationSettingsEditorWrapper(final RunnerAndConfigurationSettingsImpl settings) {
    myEditor = new ConfigurationSettingsEditor(settings);
    Disposer.register(this, myEditor);

    final DefaultComboBoxModel comboBoxModel = (DefaultComboBoxModel)myCompileMethod.getModel();
    for (String method : RunManagerConfig.METHODS) {
      comboBoxModel.addElement(method);
    }
    final RunConfiguration runConfiguration = settings.getConfiguration();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());
    myCompileBeforeRunning = runManager.getCompileMethodBeforeRun(runConfiguration);
    myCompileMethod.setSelectedItem(myCompileBeforeRunning);

    myCompileMethod.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myCompileBeforeRunning = (String)myCompileMethod.getSelectedItem();
      }
    });

    myStoreProjectConfiguration = runManager.isConfigurationShared(settings);
    myCbStoreProjectConfiguration.setSelected(myStoreProjectConfiguration);
    myCbStoreProjectConfiguration.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myStoreProjectConfiguration = myCbStoreProjectConfiguration.isSelected();
      }
    });
  }

  public void setCompileMethodState(boolean state){
    myCompileMethod.setEnabled(state);
  }

  @NotNull
  protected JComponent createEditor() {
    myComponentPlace.setLayout(new GridBagLayout());
    myComponentPlace.add(myEditor.getComponent(), new GridBagConstraints(0,0,1,1,1.0,1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0,0,0,0), 0,0));
    myComponentPlace.doLayout();
    return myWholePanel;
  }

  protected void disposeEditor() {
  }

  public void resetEditorFrom(final RunnerAndConfigurationSettingsImpl settings) {
    myEditor.resetEditorFrom(settings);
  }

  public void applyEditorTo(final RunnerAndConfigurationSettingsImpl settings) throws ConfigurationException {
    myEditor.applyEditorTo(settings);
    additionalApply(settings);
  }

  private void additionalApply(final RunnerAndConfigurationSettingsImpl settings) {
    final RunConfiguration runConfiguration = settings.getConfiguration();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());
    runManager.setCompileMethodBeforeRun(runConfiguration, myCompileBeforeRunning);
    runManager.shareConfiguration(runConfiguration, myStoreProjectConfiguration);
  }

  public RunnerAndConfigurationSettingsImpl getSnapshot() throws ConfigurationException {
    return myEditor.getSnapshot();
  }


  public String getCompileMethodBeforeRunning() {
    return myCompileBeforeRunning;
  }

  public boolean isStoreProjectConfiguration() {
    return myStoreProjectConfiguration;
  }
}
