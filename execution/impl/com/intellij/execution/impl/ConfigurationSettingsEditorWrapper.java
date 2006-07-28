package com.intellij.execution.impl;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import java.util.Set;

/**
 * User: anna
 * Date: 27-Mar-2006
 */
public class ConfigurationSettingsEditorWrapper extends SettingsEditor<RunnerAndConfigurationSettingsImpl> {

  private JPanel myComponentPlace;
  private JCheckBox myCbStoreProjectConfiguration;
  private JPanel myWholePanel;

  private JPanel myStepsPanel;
  private Map<String,Boolean> myStepsBeforeLaunch;
  private final StepBeforeLaunchRow[] myStepsBeforeLaunchRows;

  private boolean myStoreProjectConfiguration;

  private ConfigurationSettingsEditor myEditor;

  public ConfigurationSettingsEditorWrapper(final RunnerAndConfigurationSettingsImpl settings) {
    myEditor = new ConfigurationSettingsEditor(settings);
    Disposer.register(this, myEditor);

    final RunConfiguration runConfiguration = settings.getConfiguration();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());

    myStepsBeforeLaunch = runManager.getCompileMethodBeforeRun(runConfiguration);

    final Set<String> list = runManager.getPossibleActionsBeforeRun();
    myStepsBeforeLaunchRows = new StepBeforeLaunchRow[list.size()];
    myStepsPanel.setLayout(new GridLayout(list.size(), 1));
    int idx = 0;
    for (final String method : list) {
      final StepBeforeLaunchRow stepRow = new StepBeforeLaunchRow(runManager, runConfiguration, myStepsBeforeLaunch, method);
      myStepsPanel.add(stepRow);
      myStepsBeforeLaunchRows[idx++] = stepRow;
    }

    myStoreProjectConfiguration = runManager.isConfigurationShared(settings);
    myCbStoreProjectConfiguration.setSelected(myStoreProjectConfiguration);
    myCbStoreProjectConfiguration.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myStoreProjectConfiguration = myCbStoreProjectConfiguration.isSelected();
      }
    });
  }

  public void setCompileMethodState(boolean state){
    for (StepBeforeLaunchRow stepRow : myStepsBeforeLaunchRows) {
      stepRow.enableRow(state);
    }
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
    runManager.setCompileMethodBeforeRun(runConfiguration, myStepsBeforeLaunch);
    runManager.shareConfiguration(runConfiguration, myStoreProjectConfiguration);
  }

  public RunnerAndConfigurationSettingsImpl getSnapshot() throws ConfigurationException {
    return myEditor.getSnapshot();
  }


  public Map<String, Boolean> getStepsBeforeLaunch() {
    return myStepsBeforeLaunch;
  }

  public boolean isStoreProjectConfiguration() {
    return myStoreProjectConfiguration;
  }

  private class StepBeforeLaunchRow extends JPanel {
    private JCheckBox myCheckBox;
    private FixedSizeButton myButton;

    public StepBeforeLaunchRow(final RunManagerImpl runManager,
                              final RunConfiguration runConfiguration,
                              final Map<String, Boolean> methodsBeforeRun,
                              final String methodName) {
      super(new GridBagLayout());
      final Boolean checked = methodsBeforeRun.get(methodName);
      myCheckBox = new JCheckBox(methodName, checked != null && checked.booleanValue());
      GridBagConstraints gc = new GridBagConstraints(GridBagConstraints.RELATIVE, 0 , 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 0);
      add(myCheckBox, gc);
      final Function<RunConfiguration, String> actionByName = runManager.getActionByName(methodName);
      gc.weightx = 1;
      if (actionByName != null) {
        final String descriptionByName = runManager.getDescriptionByName(methodName, runConfiguration);
        myCheckBox.setText(getCheckboxText(descriptionByName, methodName));

        myButton = new FixedSizeButton(20);
        add(myButton, gc);

        myButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            final String description = actionByName.fun(runConfiguration);
            myCheckBox.setText(getCheckboxText(description, methodName));
          }
        });
      } else {
        add(Box.createHorizontalBox(), gc);
      }
      enableSettings();
      myCheckBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          methodsBeforeRun.put(methodName, myCheckBox.isSelected());
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
