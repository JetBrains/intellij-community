// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.SyntheticRunConfigurationBase;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.statistics.RunConfigurationOptionUsagesCollector;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CreateAction extends BaseRunConfigurationAction {
  public CreateAction() {
    this(null);
  }

  public CreateAction(@Nullable Icon icon) {
    super(ExecutionBundle.messagePointer("create.run.configuration.action.name"), Presentation.NULL_STRING, icon);
    getTemplatePresentation().putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true);
  }

  @Override
  protected void perform(@NotNull RunnerAndConfigurationSettings configurationSettings,
                         @NotNull ConfigurationContext context) {
    choosePolicy(context).perform(configurationSettings, context);
  }

  @Override
  protected void updatePresentation(@NotNull Presentation presentation, final @NotNull String actionText, final ConfigurationContext context) {
    choosePolicy(context).update(presentation, context, actionText);
  }

  private BaseCreatePolicy choosePolicy(final ConfigurationContext context) {
    final RunnerAndConfigurationSettings configuration = findExisting(context);
    if (configuration == null) {
      return Holder.CREATE_AND_EDIT;
    }
    if (configuration.getConfiguration() instanceof SyntheticRunConfigurationBase) {
      return Holder.DISABLED;
    }
    return Holder.EDIT;
  }

  private abstract static class BaseCreatePolicy {
    public void update(final Presentation presentation, final ConfigurationContext context, final @NotNull String actionText) {
      updateText(presentation, actionText);
    }
    
    protected abstract void updateText(final Presentation presentation, final String actionText);

    protected abstract void perform(RunnerAndConfigurationSettings configurationSettings, ConfigurationContext context);
  }

  private static class CreateAndEditPolicy extends BaseCreatePolicy {
    @Override
    protected void updateText(final Presentation presentation, final String actionText) {
      presentation.setText(ExecutionBundle.message("create.run.configuration.action.name"), false);
    }

    @Override
    protected void perform(RunnerAndConfigurationSettings configuration, ConfigurationContext context) {
      if (ApplicationManager.getApplication().isUnitTestMode() ||
          RunDialog.editConfiguration(context.getProject(), configuration,
                                      ExecutionBundle.message("create.run.configuration.for.item.dialog.title", configuration.getName()))) {
        final RunManagerImpl runManager = (RunManagerImpl)context.getRunManager();
        runManager.addConfiguration(configuration);
        runManager.setSelectedConfiguration(configuration);
        RunConfigurationOptionUsagesCollector.logAddNew(context.getProject(), configuration.getType().getId(), context.getPlace());
      }
    }
  }

  private static final class EditPolicy extends CreateAndEditPolicy {
    @Override
    protected void perform(RunnerAndConfigurationSettings configuration, ConfigurationContext context) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        RunDialog.editConfiguration(context.getProject(), configuration,
                                    ExecutionBundle.message("edit.run.configuration.for.item.dialog.title", configuration.getName()));
      }
    }
  }

  private static final class Holder {
    private static final BaseCreatePolicy CREATE_AND_EDIT = new CreateAndEditPolicy();
    private static final BaseCreatePolicy EDIT = new EditPolicy();
    private static final BaseCreatePolicy DISABLED = new BaseCreatePolicy() {
      @Override
      public void update(Presentation presentation, ConfigurationContext context, @NotNull String actionText) {
        presentation.setEnabledAndVisible(false);
      }

      @Override
      protected void updateText(Presentation presentation, String actionText) {
      }

      @Override
      protected void perform(RunnerAndConfigurationSettings configurationSettings, ConfigurationContext context) {
      }
    };
  }
}
