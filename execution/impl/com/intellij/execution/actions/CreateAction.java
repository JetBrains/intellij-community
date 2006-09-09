package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.actionSystem.Presentation;

public class CreateAction extends BaseRunConfigurationAction {
  public CreateAction() {
    super(ExecutionBundle.message("create.run.configuration.action.name"), null, null);
  }

  protected void perform(final ConfigurationContext context) {
    choosePolicy(context).perform(context);
  }

  protected void updatePresentation(final Presentation presentation, final String actionText, final ConfigurationContext context) {
    choosePolicy(context).update(presentation, context, actionText);
  }

  private static BaseCreatePolicy choosePolicy(final ConfigurationContext context) {
    final RunnerAndConfigurationSettings configuration = context.findExisting();
    if (configuration == null) return CREATE_AND_EDIT;
    final RunManagerEx runManager = context.getRunManager();
    if (runManager.getSelectedConfiguration() != configuration) return SELECT;
    if (runManager.isTemporary(configuration.getConfiguration())) return SAVE;
    return SELECTED_STABLE;
  }



  private static abstract class BaseCreatePolicy {

    public enum ActionType {
      CREATE, SAVE, SELECT
    }

    private final ActionType myType;

    public BaseCreatePolicy(final ActionType type) {
      myType = type;
    }

    public void update(final Presentation presentation, final ConfigurationContext context, final String actionText) {
      updateText(presentation, actionText);
      presentation.setIcon(context.getConfiguration().getFactory().getIcon());
    }

    protected void updateText(final Presentation presentation, final String actionText) {
      presentation.setText(generateName(actionText), false);
    }

    private String generateName(final String actionText) {
      switch(myType) {
        case CREATE: return ExecutionBundle.message("create.run.configuration.for.item.action.name", actionText);
        case SELECT: return ExecutionBundle.message("select.run.configuration.for.item.action.name", actionText);
        default:  return ExecutionBundle.message("save.run.configuration.for.item.action.name", actionText);
      }
    }

    public abstract void perform(ConfigurationContext context);
  }

  private static class SelectPolicy extends BaseCreatePolicy {
    public SelectPolicy() {
      super(ActionType.SELECT);
    }

    public void perform(final ConfigurationContext context) {
      final RunnerAndConfigurationSettingsImpl configuration = context.findExisting();
      if (configuration == null) return;
      context.getRunManager().setActiveConfiguration(configuration);
    }
  }

  private static class CreatePolicy extends BaseCreatePolicy {
    public CreatePolicy() {
      super(ActionType.CREATE);
    }

    public void perform(final ConfigurationContext context) {
      final RunManagerImpl runManager = (RunManagerImpl)context.getRunManager();
      final RunnerAndConfigurationSettingsImpl configuration = context.getConfiguration();
      final RunnerAndConfigurationSettingsImpl template = runManager.getConfigurationTemplate(configuration.getFactory());
      final RunConfiguration templateConfiguration = template.getConfiguration();
      runManager.addConfiguration(configuration, runManager.isConfigurationShared(template), runManager.getStepsBeforeLaunch(
        templateConfiguration));
      runManager.createStepsBeforeRun(template, configuration.getConfiguration());
      runManager.setActiveConfiguration(configuration);
    }
  }

  private static class CreateAndEditPolicy extends CreatePolicy {
    protected void updateText(final Presentation presentation, final String actionText) {
      presentation.setText(ExecutionBundle.message("create.run.configuration.for.item.action.name", actionText) + "...", false);
    }

    public void perform(final ConfigurationContext context) {
      final RunnerAndConfigurationSettingsImpl configuration = context.getConfiguration();
      if (RunDialog.editConfiguration(context.getProject(), configuration, ExecutionBundle.message("create.run.configuration.for.item.dialog.title", configuration.getName())))
        super.perform(context);
    }
  }

  private static class SavePolicy extends BaseCreatePolicy {
    public SavePolicy() {
      super(ActionType.SAVE);
    }

    public void perform(final ConfigurationContext context) {
      RunnerAndConfigurationSettings settings = context.findExisting();
      if (settings != null) context.getRunManager().makeStable(settings.getConfiguration());
    }
  }

  private static final BaseCreatePolicy CREATE_AND_EDIT = new CreateAndEditPolicy();
  private static final BaseCreatePolicy SELECT = new SelectPolicy();
  private static final BaseCreatePolicy SAVE = new SavePolicy();
  private static final BaseCreatePolicy SELECTED_STABLE = new BaseCreatePolicy(BaseCreatePolicy.ActionType.SELECT) {
    public void perform(final ConfigurationContext context) {}

    public void update(final Presentation presentation, final ConfigurationContext context, final String actionText) {
      super.update(presentation, context, actionText);
      presentation.setVisible(false);
    }
  };
}
