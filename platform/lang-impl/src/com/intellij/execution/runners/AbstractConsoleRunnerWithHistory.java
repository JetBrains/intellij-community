/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionHelper;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.console.*;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.SideBorder;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author oleg
 *         This class provides basic functionality for running consoles.
 *         It launches external process and handles line input with history
 */
public abstract class AbstractConsoleRunnerWithHistory<T extends LanguageConsoleView> {
  private final String myConsoleTitle;

  private ProcessHandler myProcessHandler;
  private final String myWorkingDir;

  private T myConsoleView;

  private final Project myProject;

  private ProcessBackedConsoleExecuteActionHandler myConsoleExecuteActionHandler;

  public AbstractConsoleRunnerWithHistory(@NotNull Project project, @NotNull String consoleTitle, @Nullable String workingDir) {
    myProject = project;
    myConsoleTitle = consoleTitle;
    myWorkingDir = workingDir;
  }

  /**
   * Launch process, setup history, actions etc.
   *
   * @throws ExecutionException
   */
  public void initAndRun() throws ExecutionException {
    // Create Server process
    final Process process = createProcess();
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        initConsoleUI(process);
      }
    });
  }

  private void initConsoleUI(Process process) {
    // Init console view
    myConsoleView = createConsoleView();
    if (myConsoleView instanceof LanguageConsoleViewImpl) {
      ((LanguageConsoleViewImpl)myConsoleView).setBorder(new SideBorder(UIUtil.getBorderColor(), SideBorder.LEFT));
    }
    myProcessHandler = createProcessHandler(process);

    myConsoleExecuteActionHandler = createExecuteActionHandler();

    ProcessTerminatedListener.attach(myProcessHandler);

    myProcessHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        finishConsole();
      }
    });

    // Attach to process
    myConsoleView.attachToProcess(myProcessHandler);

    // Runner creating
    final Executor defaultExecutor = DefaultRunExecutor.getRunExecutorInstance();
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false);

    // Runner creating
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
    panel.add(myConsoleView.getComponent(), BorderLayout.CENTER);

    actionToolbar.setTargetComponent(panel);

    final RunContentDescriptor contentDescriptor =
      new RunContentDescriptor(myConsoleView, myProcessHandler, panel, constructConsoleTitle(myConsoleTitle));

    contentDescriptor.setFocusComputable(new Computable<JComponent>() {
      @Override
      public JComponent compute() {
        return getLanguageConsole().getConsoleEditor().getContentComponent();
      }
    });
    contentDescriptor.setAutoFocusContent(isAutoFocusContent());


    // tool bar actions
    final List<AnAction> actions = fillToolBarActions(toolbarActions, defaultExecutor, contentDescriptor);
    registerActionShortcuts(actions, getLanguageConsole().getConsoleEditor().getComponent());
    registerActionShortcuts(actions, panel);
    panel.updateUI();
    showConsole(defaultExecutor, contentDescriptor);

    // Run
    myProcessHandler.startNotify();
  }

  private String constructConsoleTitle(final @NotNull String consoleTitle) {
    if (shouldAddNumberToTitle()) {
      List<RunContentDescriptor> consoles = ExecutionHelper.collectConsolesByDisplayName(myProject, new NotNullFunction<String, Boolean>() {
        @NotNull
        @Override
        public Boolean fun(String dom) {
          return dom.contains(consoleTitle);
        }
      });
      int max = 0;
      for (RunContentDescriptor dsc : consoles) {
        ProcessHandler handler = dsc.getProcessHandler();
        if (handler != null && !handler.isProcessTerminated()) {
          if (max == 0) {
            max = 1;
          }
          try {
            int num = Integer.parseInt(dsc.getDisplayName().substring(consoleTitle.length() + 1, dsc.getDisplayName().length() - 1));
            if (num > max) {
              max = num;
            }
          }
          catch (Exception ignored) {
            //skip
          }
        }
      }
      if (max >= 1) {
        return consoleTitle + "(" + (max + 1) + ")";
      }
    }

    return consoleTitle;
  }

  public boolean isAutoFocusContent() {
    return true;
  }

  protected boolean shouldAddNumberToTitle() {
    return false;
  }

  protected void showConsole(Executor defaultExecutor, RunContentDescriptor myDescriptor) {
    // Show in run toolwindow
    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(defaultExecutor, myDescriptor);
  }

  protected void finishConsole() {
    myConsoleView.getConsole().setEditable(false);
  }

  protected abstract T createConsoleView();

  @Nullable
  protected abstract Process createProcess() throws ExecutionException;

  protected abstract OSProcessHandler createProcessHandler(final Process process);

  public static void registerActionShortcuts(final List<AnAction> actions, final JComponent component) {
    for (AnAction action : actions) {
      if (action.getShortcutSet() != null) {
        action.registerCustomShortcutSet(action.getShortcutSet(), component);
      }
    }
  }

  protected List<AnAction> fillToolBarActions(final DefaultActionGroup toolbarActions,
                                              final Executor defaultExecutor,
                                              final RunContentDescriptor contentDescriptor) {

    List<AnAction> actionList = ContainerUtil.newArrayList();

    //stop
    actionList.add(createStopAction());

    //close
    actionList.add(createCloseAction(defaultExecutor, contentDescriptor));

    // run action
    actionList.add(createConsoleExecAction(myConsoleExecuteActionHandler));

    // Help
    actionList.add(CommonActionsManager.getInstance().createHelpAction("interactive_console"));

    toolbarActions.addAll(actionList);

    return actionList;
  }

  protected AnAction createCloseAction(final Executor defaultExecutor, final RunContentDescriptor myDescriptor) {
    return new CloseAction(defaultExecutor, myDescriptor, myProject);
  }

  protected AnAction createStopAction() {
    return ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
  }

  public LanguageConsoleImpl getLanguageConsole() {
    return myConsoleView.getConsole();
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * @deprecated to remove in IDEA 14
   */
  public static AnAction createConsoleExecAction(@NotNull LanguageConsoleView console,
                                                 @NotNull ProcessHandler processHandler,
                                                 @NotNull ProcessBackedConsoleExecuteActionHandler consoleExecuteActionHandler) {
    return new ConsoleExecuteAction(console, consoleExecuteActionHandler, consoleExecuteActionHandler.getEmptyExecuteAction(), consoleExecuteActionHandler);
  }

  protected AnAction createConsoleExecAction(@NotNull ProcessBackedConsoleExecuteActionHandler consoleExecuteActionHandler) {
    return new ConsoleExecuteAction(myConsoleView, consoleExecuteActionHandler, consoleExecuteActionHandler.getEmptyExecuteAction(), consoleExecuteActionHandler);
  }

  @SuppressWarnings("UnusedDeclaration")
  @Deprecated
  /**
   * @deprecated to remove in IDEA 14
   */
  public static AnAction createConsoleExecAction(LanguageConsoleImpl languageConsole,
                                                 ProcessHandler processHandler,
                                                 @SuppressWarnings("deprecation") ConsoleExecuteActionHandler consoleExecuteActionHandler) {
    return ConsoleExecuteAction.createAction(languageConsole, consoleExecuteActionHandler);
  }

  @NotNull
  protected ProcessBackedConsoleExecuteActionHandler createExecuteActionHandler() {
    //noinspection deprecation
    return createConsoleExecuteActionHandler();
  }

  @SuppressWarnings({"UnusedDeclaration", "deprecation"})
  @Deprecated
  /**
   * @deprecated to remove in IDEA 14
   */
  protected ConsoleExecuteActionHandler createConsoleExecuteActionHandler() {
    throw new AbstractMethodError();
  }

  public T getConsoleView() {
    return myConsoleView;
  }

  public Project getProject() {
    return myProject;
  }

  public String getConsoleTitle() {
    return myConsoleTitle;
  }

  public String getWorkingDir() {
    return myWorkingDir;
  }

  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  public ProcessBackedConsoleExecuteActionHandler getConsoleExecuteActionHandler() {
    return myConsoleExecuteActionHandler;
  }
}
