package com.intellij.openapi.externalSystem.action;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 * @since 22.05.13 12:57
 */
public class RunExternalTaskAction extends AbstractExternalTaskAction {

  public RunExternalTaskAction() {
    getTemplatePresentation().setText(ExternalSystemBundle.message("action.task.run.text"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.message("action.task.run.description"));
  }

  @Nullable
  @Override
  protected String getVmOptions() {
    return null;
  }

  @Override
  protected boolean prepareForOutput() {
    //RunnerAndConfigurationSettings settings = new RunnerAndConfigurationSettingsImpl();
    return true;
  }

  @Override
  protected void onOutput(@NotNull String text, @NotNull ConsoleViewContentType type) {
    // TODO den remove
    System.out.print(text);
  }
}
