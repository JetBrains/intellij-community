// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NotNull;

public class RunAnythingTemporaryRunConfigurationGroup extends RunAnythingRunConfigurationGroup {
  @NotNull
  @Override
  public String getTitle() {
    return IdeBundle.message("run.anything.group.title.temporary");
  }

  @NotNull
  @Override
  public String getVisibilityKey() {
    return "run.anything.settings.temporary.configurations";
  }

  @Override
  public boolean shouldBeShownInitially() {
    return true;
  }

  @Override
  protected boolean isTemporary(@NotNull ChooseRunConfigurationPopup.ItemWrapper wrapper) {
    Object value = wrapper.getValue();
    return value instanceof RunnerAndConfigurationSettings && ((RunnerAndConfigurationSettings)value).isTemporary();
  }
}