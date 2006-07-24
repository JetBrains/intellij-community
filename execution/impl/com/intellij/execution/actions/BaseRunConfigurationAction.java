package com.intellij.execution.actions;

import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

import javax.swing.*;

abstract class BaseRunConfigurationAction extends AnAction {
  protected BaseRunConfigurationAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final ConfigurationContext context = new ConfigurationContext(dataContext);
    final RunnerAndConfigurationSettings configuration = context.getConfiguration();
    if (configuration == null) return;
    final Project project = context.getProject();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    final RunnerAndConfigurationSettingsImpl template = runManager.getConfigurationTemplate(configuration.getFactory());
    final RunConfiguration templateConfiguration = template.getConfiguration();
    runManager.createStepsBeforeRun(template, configuration.getConfiguration());
    runManager.setCompileMethodBeforeRun(configuration.getConfiguration(), runManager.getCompileMethodBeforeRun(templateConfiguration));
    perform(context);
  }

  protected abstract void perform(ConfigurationContext context);

  public void update(final AnActionEvent event){
    final ConfigurationContext context = new ConfigurationContext(event.getDataContext());
    final Presentation presentation = event.getPresentation();
    final RunnerAndConfigurationSettings configuration = context.getConfiguration();
    if (configuration == null){
      presentation.setEnabled(false);
      presentation.setVisible(false);
    }
    else{
      presentation.setEnabled(true);
      presentation.setVisible(true);
      final String name = suggestRunActionName((LocatableConfiguration)configuration.getConfiguration());
      updatePresentation(presentation, " " + name, context);
    }
  }

  public static String suggestRunActionName(final LocatableConfiguration configuration) {
    if (!configuration.isGeneratedName()) {
      return "\"" + ExecutionUtil.shortenName(configuration.getName(), 0) + "\"";
    } else return "\"" + configuration.suggestedName() + "\"";
  }

  protected abstract void updatePresentation(Presentation presentation, String actionText, ConfigurationContext context);

}
