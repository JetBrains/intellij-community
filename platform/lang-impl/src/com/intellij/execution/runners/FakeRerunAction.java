/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Roman.Chernyatchik
 */
class FakeRerunAction extends AnAction implements DumbAware {
  @SuppressWarnings("deprecation")
  static final List<RestartAction> registry = ContainerUtil.createLockFreeCopyOnWriteList();

  @Override
  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    ExecutionEnvironment environment = getEnvironment(event);
    if (environment != null) {
      presentation.setText(ExecutionBundle.message("rerun.configuration.action.name", environment.getRunProfile().getName()));
      presentation.setIcon(ExecutionManagerImpl.isProcessRunning(getDescriptor(event)) ? AllIcons.Actions.Restart : environment.getExecutor().getIcon());
      presentation.setEnabledAndVisible(isEnabled(event));
      return;
    }

    FakeRerunAction action = findActualAction(event);
    presentation.setEnabled(action != null && action.isEnabled(event));
    presentation.setVisible(false);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    ExecutionEnvironment environment = getEnvironment(event);
    if (environment != null) {
      ExecutionUtil.restart(environment);
      return;
    }

    FakeRerunAction action = findActualAction(event);
    if (action != null && action.isEnabled(event)) {
      action.actionPerformed(event);
    }
  }

  @Nullable
  protected RunContentDescriptor getDescriptor(AnActionEvent event) {
    return event.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
  }

  @Nullable
  protected ExecutionEnvironment getEnvironment(AnActionEvent event) {
    return event.getData(LangDataKeys.EXECUTION_ENVIRONMENT);
  }

  protected boolean isEnabled(AnActionEvent event) {
    RunContentDescriptor descriptor = getDescriptor(event);
    ProcessHandler processHandler = descriptor == null ? null : descriptor.getProcessHandler();
    ExecutionEnvironment environment = getEnvironment(event);
    return environment != null &&
           !ExecutorRegistry.getInstance().isStarting(environment) &&
           !(processHandler != null && processHandler.isProcessTerminating());
  }

  @Nullable
  private JComponent getRunComponent(@NotNull AnActionEvent event) {
    RunContentDescriptor descriptor = getDescriptor(event);
    return descriptor == null ? null : descriptor.getComponent();
  }

  @Nullable
  private static FakeRerunAction findActualAction(@NotNull final AnActionEvent event) {
    if (registry.isEmpty()) {
      return null;
    }

    List<FakeRerunAction> candidates = new ArrayList<FakeRerunAction>(registry);
    Collections.sort(candidates, new Comparator<FakeRerunAction>() {
      @Override
      public int compare(@NotNull FakeRerunAction action1, @NotNull FakeRerunAction action2) {
        boolean isActive1 = action1.isEnabled(event);
        if (isActive1 != action2.isEnabled(event)) {
          return isActive1 ? -1 : 1;
        }

        JComponent component1 = action1.getRunComponent(event);
        JComponent component2 = action2.getRunComponent(event);
        Window window1 = component1 == null ? null : SwingUtilities.windowForComponent(component1);
        Window window2 = component2 == null ? null : SwingUtilities.windowForComponent(component2);
        if (window1 == null) {
          return 1;
        }
        if (window2 == null) {
          return -1;
        }

        boolean showing1 = component1.isShowing();
        boolean showing2 = component2.isShowing();
        if (showing1 && !showing2) {
          return -1;
        }
        if (showing2 && !showing1) {
          return 1;
        }
        return (window1.isActive() ? -1 : 1);
      }
    });
    return candidates.get(0);
  }
}
