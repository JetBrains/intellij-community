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

import com.intellij.execution.*;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
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

  private ProcessHandler myProcessHandler;
  private final ProgramRunner myRunner;
  private final RunContentDescriptor myDescriptor;
  @NotNull private final Executor myExecutor;
  private final Icon myIcon;
  private final ExecutionEnvironment myEnvironment;

  public RestartAction(@NotNull final Executor executor,
                       final ProgramRunner runner,
                       final ProcessHandler processHandler,
                       final Icon icon,
                       final RunContentDescriptor descriptor,
                       @NotNull final ExecutionEnvironment env) {
    getTemplatePresentation().setIcon(icon);
    Disposer.register(descriptor, this);
    registry.add(this);

    myIcon = icon;
    myEnvironment = env;
    getTemplatePresentation().setEnabled(false);
    myProcessHandler = processHandler;
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

  public void actionPerformed(final AnActionEvent e) {
    Project project = myEnvironment.getProject();
    RunnerAndConfigurationSettings settings = myEnvironment.getRunnerAndConfigurationSettings();
    if (project == null)
      return;
    if (settings != null) {
      ExecutionManager.getInstance(project).restartRunProfile(project,
                                                              myExecutor,
                                                              myEnvironment.getExecutionTarget(),
                                                              settings,
                                                              myProcessHandler);
    }
    else {
      restart();
    }
  }

  //Should be used by android framework only
  public void restart() {
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myDescriptor.getComponent()));
    if (ExecutorRegistry.getInstance().isStarting(project, myExecutor.getId(), myRunner.getRunnerId())) {
      return;
    }
    try {
      final ExecutionEnvironment old = myEnvironment;
      myRunner.execute(myExecutor, new ExecutionEnvironment(old.getRunProfile(),
                                                            old.getExecutionTarget(),
                                                            project,
                                                            old.getRunnerSettings(),
                                                            old.getConfigurationSettings(),
                                                            myDescriptor,
                                                            old.getRunnerAndConfigurationSettings()));
    }
    catch (RunCanceledByUserException ignore) {
    }
    catch (ExecutionException e1) {
      Messages.showErrorDialog(project, e1.getMessage(), ExecutionBundle.message("restart.error.message.title"));
    }
  }

  public void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    String name = myEnvironment.getRunProfile().getName();
    final boolean isRunning = myProcessHandler != null && !myProcessHandler.isProcessTerminated();

    presentation.setText(ExecutionBundle.message("rerun.configuration.action.name", name));
    presentation.setIcon(isRunning ? AllIcons.Actions.Restart : myIcon);
    presentation.setEnabled(isEnabled());
  }

  boolean isEnabled() {
    boolean isTerminating = myProcessHandler != null && myProcessHandler.isProcessTerminating();
    boolean isStarting = ExecutorRegistry.getInstance().isStarting(myEnvironment.getProject(), myExecutor.getId(), myRunner.getRunnerId());
    return !isStarting && !isTerminating;
  }

  public void registerShortcut(final JComponent component) {
    registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_RERUN)),
                              component);
  }
}
