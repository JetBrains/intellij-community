// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BeforeRunFragment<S extends RunConfigurationBase<?>> extends RunConfigurationEditorFragment<S, JComponent> {
  private final BeforeRunComponent myComponent;
  private final Key<?> myKey;
  private RunnerAndConfigurationSettingsImpl mySettings;

  public static <S extends RunConfigurationBase<?>> List<SettingsEditorFragment<S, ?>> createGroup() {
    List<SettingsEditorFragment<S, ?>> list = new ArrayList<>();
    SettingsEditorFragment<S, ?> tag =
      RunConfigurationEditorFragment.createSettingsTag("before.launch.openToolWindow",
                                                       ExecutionBundle.message("run.configuration.before.run.open.tool.window"),
                                                       ExecutionBundle.message("run.configuration.before.run.group"),
                                                       settings -> settings.isActivateToolWindowBeforeRun(),
                                                       (settings, value) -> settings.setActivateToolWindowBeforeRun(value), 100);
    tag.setActionHint(ExecutionBundle.message("open.the.run.debug.tool.window.when.the.application.is.started"));
    list.add(tag);
    SettingsEditorFragment<S, ?> tag1 =
      RunConfigurationEditorFragment.createSettingsTag("before.launch.editSettings",
                                                       ExecutionBundle.message("run.configuration.before.run.edit.settings"),
                                                       ExecutionBundle.message("run.configuration.before.run.group"),
                                                       settings -> settings.isEditBeforeRun(),
                                                       (settings, value) -> settings.setEditBeforeRun(value), 100);
    tag1.setActionHint(ExecutionBundle.message("open.the.settings.for.this.run.debug.configuration.each.time.it.is.run"));
    list.add(tag1);
    return list;
  }

  public static <S extends RunConfigurationBase<?>> BeforeRunFragment<S> createBeforeRun(@NotNull BeforeRunComponent component, Key<?> key) {
    return new BeforeRunFragment<>(component, key);
  }

  private BeforeRunFragment(@NotNull BeforeRunComponent component, Key<?> key) {
    super("beforeRunTasks", ExecutionBundle.message("run.configuration.before.run.task"),
          ExecutionBundle.message("run.configuration.before.run.group"), wrap(component), -2);
    myComponent = component;
    myKey = key;
    component.myChangeListener = () -> fireEditorStateChanged();
    setActionHint(ExecutionBundle.message("specify.tasks.to.be.performed.before.starting.the.application"));
  }

  @Override
  public boolean isInitiallyVisible(S s) {
    return ContainerUtil.exists(mySettings.getManager().getBeforeRunTasks(s), task -> task.getProviderId() != myKey);
  }

  private static JComponent wrap(BeforeRunComponent component) {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel labelPanel = new JPanel(new BorderLayout());
    JLabel label = new JLabel(ExecutionBundle.message("run.configuration.before.run.label"));
    label.setBorder(JBUI.Borders.empty(JBUI.scale(12), 0, 0, JBUI.scale(5)));
    labelPanel.add(label, BorderLayout.NORTH);
    panel.add(labelPanel, BorderLayout.WEST);
    panel.add(component, BorderLayout.CENTER);
    return panel;
  }

  @Override
  public int getMenuPosition() {
    return 100;
  }

  @Override
  public void toggle(boolean selected) {
    super.setSelected(selected);
    if (selected) {
      myComponent.showPopup();
    }
  }

  @Override
  public void resetEditorFrom(@NotNull RunnerAndConfigurationSettingsImpl s) {
    mySettings = s;
    myComponent.reset(mySettings);
  }

  @Override
  public void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s) {
    if (isSelected()) {
      myComponent.apply(s);
    }
    else {
      s.getManager().setBeforeRunTasks(s.getConfiguration(), Collections.<BeforeRunTask<?>>emptyList());
    }
  }
}
