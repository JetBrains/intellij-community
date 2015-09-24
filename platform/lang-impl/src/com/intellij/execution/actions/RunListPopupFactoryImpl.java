package com.intellij.execution.actions;

import com.intellij.execution.Executor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import org.jetbrains.annotations.NotNull;

public class RunListPopupFactoryImpl implements RunListPopupFactory {
  @NotNull
  @Override
  public RunListPopup createRunListPopup(ChooseRunConfigurationPopup chooseRunConfigurationPopup,
                                         Executor alternativeExecutor,
                                         Project project,
                                         ListPopupStep step) {
    return new RunListPopup(chooseRunConfigurationPopup, alternativeExecutor, project, step);
  }
}
