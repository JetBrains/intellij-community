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
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author dyoma
 */
public class RestartAction extends FakeRerunAction implements DumbAware, AnAction.TransparentUpdate, Disposable {

  private final ProgramRunner myRunner;
  @NotNull private final RunContentDescriptor myDescriptor;
  @NotNull private final Executor myExecutor;
  private final ExecutionEnvironment myEnvironment;

  public RestartAction(@NotNull final Executor executor,
                       final ProgramRunner runner,
                       @NotNull final RunContentDescriptor descriptor,
                       @NotNull final ExecutionEnvironment env) {
    Disposer.register(descriptor, this);
    registry.add(this);

    myEnvironment = env;
    getTemplatePresentation().setEnabled(false);
    myRunner = runner;
    myDescriptor = descriptor;
    myExecutor = executor;
    // see IDEADEV-698
  }

  @Override
  public void dispose() {
    registry.remove(this);
  }

  @Nullable
  static RestartAction findActualAction() {
    if (registry.isEmpty())
      return null;
    List<RestartAction> candidates = new ArrayList<RestartAction>(registry);
    Collections.sort(candidates, new Comparator<RestartAction>() {
      @Override
      public int compare(RestartAction action1, RestartAction action2) {
        boolean isActive1 = action1.isEnabled();
        boolean isActive2 = action2.isEnabled();
        if (isActive1 != isActive2)
          return isActive1? - 1 : 1;
        Window window1 = SwingUtilities.windowForComponent(action1.myDescriptor.getComponent());
        Window window2 = SwingUtilities.windowForComponent(action2.myDescriptor.getComponent());
        if (window1 == null)
          return 1;
        if (window2 == null)
          return -1;
        boolean showing1 = action1.myDescriptor.getComponent().isShowing();
        boolean showing2 = action2.myDescriptor.getComponent().isShowing();
        if (showing1 && !showing2)
          return -1;
        if (showing2 && !showing1)
          return 1;
        return (window1.isActive() ? -1 : 1);
      }
    });
    return candidates.get(0);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    restart();
  }

  public void restart() {
    Project project = myEnvironment.getProject();
    if (!ExecutorRegistry.getInstance().isStarting(project, myExecutor.getId(), myRunner.getRunnerId()))
      ExecutionManager.getInstance(project).restartRunProfile(myRunner, myEnvironment, myDescriptor);
  }

  @Override
  public void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    String name = myEnvironment.getRunProfile().getName();
    ProcessHandler processHandler = myDescriptor.getProcessHandler();
    final boolean isRunning = processHandler != null && !processHandler.isProcessTerminated();

    presentation.setText(ExecutionBundle.message("rerun.configuration.action.name", name));
    presentation.setIcon(isRunning ? AllIcons.Actions.Restart : myExecutor.getIcon());
    presentation.setEnabled(isEnabled());
  }

  boolean isEnabled() {
    ProcessHandler processHandler = myDescriptor.getProcessHandler();
    boolean isTerminating = processHandler != null && processHandler.isProcessTerminating();
    boolean isStarting = ExecutorRegistry.getInstance().isStarting(myEnvironment.getProject(), myExecutor.getId(), myRunner.getRunnerId());
    return !isStarting && !isTerminating;
  }

  public void registerShortcut(final JComponent component) {
    registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_RERUN)),
                              component);
  }
}
