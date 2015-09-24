package com.intellij.execution.actions;

import com.intellij.execution.Executor;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;

public interface RunListPopupFactory {
  class SERVICE {
    @NotNull
    public static RunListPopupFactory getInstance() {
      return ServiceManager.getService(RunListPopupFactory.class);
    }
  }

  @NotNull
  ListPopupImpl createRunListPopup(ChooseRunConfigurationPopup chooseRunConfigurationPopup,
                                   Executor alternativeExecutor,
                                   Project project,
                                   ListPopupStep step);
}
