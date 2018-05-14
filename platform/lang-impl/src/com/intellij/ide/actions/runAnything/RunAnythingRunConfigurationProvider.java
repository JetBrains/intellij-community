// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.actions.ExecutorProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.runAnything.activity.RunAnythingCompletionProvider;
import com.intellij.ide.actions.runAnything.activity.RunAnythingRunConfigurationExecutionProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.ide.actions.runAnything.RunAnythingUtil.fetchProject;

public class RunAnythingRunConfigurationProvider extends RunAnythingRunConfigurationExecutionProvider
  implements RunAnythingCompletionProvider<ChooseRunConfigurationPopup.ItemWrapper> {
  @NotNull
  @Override
  public Collection<ChooseRunConfigurationPopup.ItemWrapper> getValues(@NotNull DataContext dataContext) {
    return Arrays.asList(getWrappers(dataContext));
  }

  @NotNull
  @Override
  public String getGroupTitle() {
    return IdeBundle.message("run.anything.run.configurations.group.title");
  }

  @NotNull
  private static ChooseRunConfigurationPopup.ItemWrapper[] getWrappers(@NotNull DataContext dataContext) {
    Project project = fetchProject(dataContext);
    return ChooseRunConfigurationPopup.createSettingsList(project, new ExecutorProvider() {
      @Override
      public Executor getExecutor() {
        return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.RUN);
      }
    }, false);
  }
}