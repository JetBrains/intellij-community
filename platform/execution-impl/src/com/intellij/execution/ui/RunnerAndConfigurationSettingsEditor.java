// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.RunConfigurationStorageUi;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class RunnerAndConfigurationSettingsEditor extends SettingsEditor<RunnerAndConfigurationSettings> implements
                                                                                                         TargetAwareRunConfigurationEditor {

  private final RunConfigurationFragmentedEditor<RunConfigurationBase<?>> myConfigurationEditor;
  private final @Nullable RunConfigurationStorageUi myRCStorageUi;

  public RunnerAndConfigurationSettingsEditor(RunnerAndConfigurationSettings settings,
                                              RunConfigurationFragmentedEditor<RunConfigurationBase<?>> configurationEditor) {
    super(settings.createFactory());
    myConfigurationEditor = configurationEditor;
    myConfigurationEditor.addSettingsEditorListener(editor -> fireEditorStateChanged());
    Disposer.register(this, myConfigurationEditor);

    Project project = settings.getConfiguration().getProject();
    // RunConfigurationStorageUi for non-template settings is managed by com.intellij.execution.impl.SingleConfigurationConfigurable
    myRCStorageUi = !project.isDefault() && settings.isTemplate()
                    ? new RunConfigurationStorageUi(project, () -> fireEditorStateChanged())
                    : null;
  }

  public boolean isInplaceValidationSupported() {
    return myConfigurationEditor.isInplaceValidationSupported();
  }

  @Override
  public void targetChanged(String targetName) {
    myConfigurationEditor.targetChanged(targetName);
  }

  @Override
  protected void resetEditorFrom(@NotNull RunnerAndConfigurationSettings s) {
    myConfigurationEditor.resetEditorFrom((RunnerAndConfigurationSettingsImpl)s);
    myConfigurationEditor.resetFrom((RunConfigurationBase<?>)s.getConfiguration());

    if (myRCStorageUi != null) {
      myRCStorageUi.reset(s);
    }
  }

  @Override
  protected void applyEditorTo(@NotNull RunnerAndConfigurationSettings s) throws ConfigurationException {
    myConfigurationEditor.applyEditorTo((RunnerAndConfigurationSettingsImpl)s);
    myConfigurationEditor.applyTo((RunConfigurationBase<?>)s.getConfiguration());

    if (myRCStorageUi != null) {
      // editing a template run configuration
      myRCStorageUi.apply(s);
    }
  }

  @Override
  protected @NotNull JComponent createEditor() {
    if (myRCStorageUi == null) return myConfigurationEditor.getComponent();

    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.anchor = GridBagConstraints.EAST;
    c.insets = JBUI.insets(5, 0, 5, 0);
    c.fill = GridBagConstraints.NONE;
    c.weightx = 0;
    c.weighty = 0;
    panel.add(myRCStorageUi.createComponent(), c);

    c.gridx = 0;
    c.gridy = 1;
    c.anchor = GridBagConstraints.NORTH;
    c.insets = JBUI.emptyInsets();
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1;
    c.weighty = 1;
    panel.add(myConfigurationEditor.getComponent(), c);

    return panel;
  }
}
