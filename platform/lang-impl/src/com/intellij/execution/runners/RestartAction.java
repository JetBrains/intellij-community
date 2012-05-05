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

import com.intellij.CommonBundle;
import com.intellij.execution.*;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Alarm;

import javax.swing.*;

/**
 * @author dyoma
 */
public class RestartAction extends AnAction implements DumbAware {
  private static final Icon STOP_AND_START_ICON = IconLoader.getIcon("/actions/restart.png");

  private ProcessHandler myProcessHandler;
  private final ProgramRunner myRunner;
  private final RunContentDescriptor myDescriptor;
  private final Executor myExecutor;
  private final Icon myIcon;
  private final ExecutionEnvironment myEnvironment;
  private final Alarm awaitingTerminationAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  public RestartAction(final Executor executor,
                       final ProgramRunner runner,
                       final ProcessHandler processHandler,
                       final Icon icon,
                       final RunContentDescriptor descritor,
                       final ExecutionEnvironment env) {
    super(null, null, icon);
    myIcon = icon;
    myEnvironment = env;
    getTemplatePresentation().setEnabled(false);
    myProcessHandler = processHandler;
    myRunner = runner;
    myDescriptor = descritor;
    myExecutor = executor;
    // see IDEADEV-698
  }

  public void actionPerformed(final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final RunManagerConfig config = RunManagerImpl.getInstanceImpl(myEnvironment.getProject()).getConfig();
    if (myProcessHandler != null && !myProcessHandler.isProcessTerminated() && config.isRestartRequiresConfirmation()) {
      DialogWrapper.DoNotAskOption option = new DialogWrapper.DoNotAskOption() {
        @Override
        public boolean isToBeShown() {
          return config.isRestartRequiresConfirmation();
        }

        @Override
        public void setToBeShown(boolean value, int exitCode) {
          config.setRestartRequiresConfirmation(value);
        }

        @Override
        public boolean canBeHidden() {
          return true;
        }

        @Override
        public boolean shouldSaveOptionsOnCancel() {
          return false;
        }

        @Override
        public String getDoNotShowMessage() {
          return CommonBundle.message("dialog.options.do.not.show");
        }
      };
      if (Messages.OK != Messages.showOkCancelDialog(ExecutionBundle.message("rerun.confirmation.message", myEnvironment.getRunProfile().getName()),
                                  ExecutionBundle.message("rerun.confirmation.title"), CommonBundle.message("button.ok"),
                                  CommonBundle.message("button.cancel"),
                                  Messages.getQuestionIcon(), option)) {
        return;
      }
    }
    ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM).actionPerformed(e);
    update(e);
    if (myProcessHandler != null) {
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          if (myProcessHandler == null || myProcessHandler.isProcessTerminated()) {
            doRestart(dataContext);
          }
          else {
            awaitingTerminationAlarm.addRequest(this, 100);
          }
        }
      };
      awaitingTerminationAlarm.addRequest(runnable, 100);
    }
    else {
      doRestart(dataContext);
    }
  }

  public void restart() {
    doRestart(DataManager.getInstance().getDataContext(myDescriptor.getComponent()));
  }

  private void doRestart(final DataContext dataContext) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (ExecutorRegistry.getInstance().isStarting(project, myExecutor.getId(), myRunner.getRunnerId())) {
      return;
    }
    try {
      final ExecutionEnvironment old = myEnvironment;
      myRunner.execute(myExecutor, new ExecutionEnvironment(old.getRunProfile(), project, old.getRunnerSettings(),
                                                            old.getConfigurationSettings(), myDescriptor));
    }
    catch (RunCanceledByUserException ignore) {
    }
    catch (ExecutionException e1) {
      Messages.showErrorDialog(project, e1.getMessage(), ExecutionBundle.message("restart.error.message.title"));
    }
  }

  public void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    presentation.setText(ExecutionBundle.message("rerun.configuration.action.name", myEnvironment.getRunProfile().getName()));
    final boolean isRunning = myProcessHandler != null && !myProcessHandler.isProcessTerminated();
    if (myProcessHandler != null && !isRunning) {
      myProcessHandler = null; // already terminated
    }
    presentation.setIcon(isRunning ? STOP_AND_START_ICON : myIcon);
    boolean isTerminating = myProcessHandler != null && myProcessHandler.isProcessTerminating();
    boolean isStarting = ExecutorRegistry.getInstance().isStarting(myEnvironment.getProject(), myExecutor.getId(), myRunner.getRunnerId());
    presentation.setEnabled(!isStarting && !isTerminating);
  }

  public void registerShortcut(final JComponent component) {
    registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts(IdeActions.ACTION_RERUN)),
                              component);
  }
}
