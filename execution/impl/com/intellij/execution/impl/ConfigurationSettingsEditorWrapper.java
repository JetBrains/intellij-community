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
  private JPanel myCompilationMethodPanel;

  private ConfigurationSettingsEditor myEditor;
  private Map<String,Boolean> myCompileBeforeRunning;
  private boolean myStoreProjectConfiguration;

  public ConfigurationSettingsEditorWrapper(final RunnerAndConfigurationSettingsImpl settings) {
    myEditor = new ConfigurationSettingsEditor(settings);
    Disposer.register(this, myEditor);

    final RunConfiguration runConfiguration = settings.getConfiguration();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(runConfiguration.getProject());

    myCompileBeforeRunning = runManager.getCompileMethodBeforeRun(runConfiguration);

    final Set<String> list = runManager.getPossibleActionsBeforeRun();
    myCompilationMethodPanel.setLayout(new GridBagLayout());
    int gridy = 0;
    for (final String method : list) {
      final Boolean checked = myCompileBeforeRunning.get(method);
      final JCheckBox checkBox = new JCheckBox(method, checked != null && checked.booleanValue());

      final GridBagConstraints gc =
        new GridBagConstraints(0, gridy, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0);
      myCompilationMethodPanel.add(checkBox, gc);
      final Function<RunConfiguration, String> actionByName = runManager.getActionByName(method);
      final FixedSizeButton button;
      final JLabel label;
      if (actionByName != null) {
        final String descriptionByName = runManager.getDescriptionByName(method, runConfiguration);
        label = new JLabel(descriptionByName != null ? descriptionByName : "");
        gc.gridx++;
        myCompilationMethodPanel.add(label, gc);
        button = new FixedSizeButton(20);
        gc.gridx++;
        myCompilationMethodPanel.add(button, gc);
        button.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            final String description = actionByName.fun(runConfiguration);
            label.setText(description != null ? description : "");
          }
        });
        gc.gridx++;
        gc.weightx = 1;
        myCompilationMethodPanel.add(Box.createHorizontalBox(), gc);
      } else {
        button = null;
        label = null;
      }
      enableSettings(button, checkBox, label);
      checkBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          myCompileBeforeRunning.put(method, checkBox.isSelected());
          enableSettings(button, checkBox, label);
        }
      });
      gridy++;
    }

    myStoreProjectConfiguration = runManager.isConfigurationShared(settings);
    myCbStoreProjectConfiguration.setSelected(myStoreProjectConfiguration);
    myCbStoreProjectConfiguration.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myStoreProjectConfiguration = myCbStoreProjectConfiguration.isSelected();
      }
    });
  }

  private static void enableSettings(final FixedSizeButton button, final JCheckBox checkBox, final JLabel label) {
    if (button != null && label != null) {
      button.setEnabled(checkBox.isSelected());
      label.setEnabled(checkBox.isSelected());
    }
  }

  public void setCompileMethodState(boolean state){
    UIUtil.setEnabled(myCompilationMethodPanel, state, true);
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


  public Map<String, Boolean> getCompileMethodBeforeRunning() {
    return myCompileBeforeRunning;
  }

  public boolean isStoreProjectConfiguration() {
    return myStoreProjectConfiguration;
  }
}
