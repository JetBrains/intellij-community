// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class BeforeRunFragment<S extends RunConfigurationBase<?>> extends RunConfigurationEditorFragment<S, BeforeRunComponent> {
  public static <S extends RunConfigurationBase<?>> List<SettingsEditorFragment<S, ?>> createGroup() {
    ArrayList<SettingsEditorFragment<S, ?>> list = new ArrayList<>();
    list.add(new BeforeRunFragment<>());
    list.add(RunConfigurationEditorFragment.createSettingsTag("before.launch.openToolWindow",
                                                              ExecutionBundle.message("run.configuration.before.run.open.tool.window"),
                                                              ExecutionBundle.message("run.configuration.before.run.group"),
                                                              settings -> settings.isActivateToolWindowBeforeRun(),
                                                              (settings, value) -> settings.setActivateToolWindowBeforeRun(value), 100));
    list.add(RunConfigurationEditorFragment.createSettingsTag("before.launch.editSettings",
                                                              ExecutionBundle.message("run.configuration.before.run.edit.settings"),
                                                              ExecutionBundle.message("run.configuration.before.run.group"),
                                                              settings -> settings.isEditBeforeRun(),
                                                              (settings, value) -> settings.setEditBeforeRun(value), 100));
    return list;
  }

  private BeforeRunFragment() {
    super("beforeRunTasks", ExecutionBundle.message("run.configuration.before.run.task"),
          ExecutionBundle.message("run.configuration.before.run.group"), new BeforeRunComponent(), -2);
    component().myChangeListener = () -> fireEditorStateChanged();
  }

  @Override
  public int getMenuPosition() {
    return 100;
  }

  @Override
  public void toggle(boolean selected) {
    super.setSelected(selected);
    if (selected) {
      component().showPopup();
    }
  }

  @Override
  public void resetEditorFrom(@NotNull RunnerAndConfigurationSettingsImpl s) {
    component().reset(s);
  }

  @Override
  public void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s) {
    component().apply(s);
  }
}
