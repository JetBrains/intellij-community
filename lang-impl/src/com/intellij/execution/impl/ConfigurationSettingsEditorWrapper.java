package com.intellij.execution.impl;

import com.intellij.execution.StepsBeforeRunProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

/**
 * User: anna
 * Date: 27-Mar-2006
 */
public class ConfigurationSettingsEditorWrapper extends SettingsEditor<RunnerAndConfigurationSettingsImpl> {
  private ConfigurationSettingsEditor myEditor;
  private RunnerAndConfigurationSettingsImpl mySettings;

  private Map<String,Boolean> myStepsBeforeLaunch;
  private boolean mySharedConfiguration;

  private JPanel myComponentPlace;
  private JCheckBox myCbStoreProjectConfiguration;
  private JPanel myWholePanel;

  private JPanel myStepsPanel;

  public ConfigurationSettingsEditorWrapper(final RunnerAndConfigurationSettingsImpl settings) {
    myEditor = new ConfigurationSettingsEditor(settings);
    mySettings = settings;
    Disposer.register(this, myEditor);

    additionalReset(settings);
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
    additionalReset(settings);
  }

  private void additionalReset(RunnerAndConfigurationSettingsImpl settings) {
    RunConfiguration runConfiguration = settings.getConfiguration();
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());
    myStepsBeforeLaunch = runManager.getStepsBeforeRun(runConfiguration);

    final StepsBeforeRunProvider[] providers = Extensions.getExtensions(StepsBeforeRunProvider.EXTENSION_POINT_NAME,
                                                                        runConfiguration.getProject());
    myStepsPanel.removeAll();
    if (providers.length == 0) {
      myStepsPanel.setVisible(false);
    }
    else {
      myStepsPanel.setLayout(new GridLayout(providers.length, 1));
      for (StepsBeforeRunProvider provider: providers) {
        final StepBeforeLaunchRow stepRow = new StepBeforeLaunchRow(runConfiguration, myStepsBeforeLaunch, provider);
        myStepsPanel.add(stepRow);
      }
    }

    mySharedConfiguration = runManager.isConfigurationShared(settings);
    myCbStoreProjectConfiguration.setSelected(mySharedConfiguration);
    myCbStoreProjectConfiguration.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySharedConfiguration = myCbStoreProjectConfiguration.isSelected();
      }
    });
  }

  public void applyEditorTo(final RunnerAndConfigurationSettingsImpl settings) throws ConfigurationException {
    myEditor.applyEditorTo(settings);
    additionalApply(settings);
  }

  public RunnerAndConfigurationSettingsImpl getSnapshot() throws ConfigurationException {
    RunnerAndConfigurationSettingsImpl result = myEditor.getSnapshot();
    additionalApply(result);
    return result;
  }

  private void additionalApply(final RunnerAndConfigurationSettingsImpl settings) {
    final RunConfiguration runConfiguration = settings.getConfiguration();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());
    runManager.createStepsBeforeRun(mySettings, runConfiguration);
    runManager.setStepsBeforeRun(runConfiguration, myStepsBeforeLaunch);
    runManager.shareConfiguration(runConfiguration, mySharedConfiguration);
  }

  public Map<String, Boolean> getStepsBeforeLaunch() {
    return myStepsBeforeLaunch;
  }

  public boolean isSharedConfiguration() {
    return mySharedConfiguration;
  }

  private class StepBeforeLaunchRow extends JPanel {
    private JCheckBox myCheckBox;
    private FixedSizeButton myButton;

    public StepBeforeLaunchRow(final RunConfiguration runConfiguration,
                               final Map<String, Boolean> methodsBeforeRun,
                               final StepsBeforeRunProvider provider) {
      super(new GridBagLayout());
      final Boolean checked = methodsBeforeRun.get(provider.getStepName());
      myCheckBox = new JCheckBox(provider.getStepName(), checked != null && checked.booleanValue());
      GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0 , 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 0);
      add(myCheckBox, gc);
      gc.weightx = 1;
      if (provider.hasConfigurationButton()) {
        final String descriptionByName = provider.getStepDescription(runConfiguration);
        myCheckBox.setText(getCheckboxText(descriptionByName, provider.getStepName()));

        myButton = new FixedSizeButton(20);
        add(myButton, gc);

        myButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            final String description = provider.configureStep(runConfiguration);
            myCheckBox.setText(getCheckboxText(description, provider.getStepName()));
          }
        });
      } else {
        add(Box.createHorizontalBox(), gc);
      }
      enableSettings();
      myCheckBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          methodsBeforeRun.put(provider.getStepName(), myCheckBox.isSelected());
          enableSettings();
        }
      });
    }

    private String getCheckboxText(final String description, final String methodName) {
      return methodName + " " + (description != null ? description : "");
    }

    private void enableSettings() {
      if (myButton != null) {
        myButton.setEnabled(myCheckBox.isSelected());
      }
    }

    public void enableRow(boolean state){
      myCheckBox.setEnabled(state);
      if (state) {
        enableSettings();
      } else {
        UIUtil.setEnabled(this, false, true);
      }
    }
  }
}
