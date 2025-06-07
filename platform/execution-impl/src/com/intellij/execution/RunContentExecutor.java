// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.CommonBundle;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a process and prints the output in a content tab within the Run toolwindow.
 */
public final class RunContentExecutor implements Disposable {
  private final Project myProject;
  private final ProcessHandler myProcess;
  private final List<Filter> myFilterList = new ArrayList<>();
  private Runnable myRerunAction;
  private Runnable myStopAction;
  private Runnable myAfterCompletion;
  private Computable<Boolean> myStopEnabled;
  private @NlsContexts.TabTitle String myTitle = ExecutionBundle.message("output.tab.default.title");
  private String myHelpId = null;
  private boolean myActivateToolWindow = true;
  private boolean myFocusToolWindow = true;
  /**
   * User-provided console that has to be used instead of newly created
   */
  private ConsoleView myUserProvidedConsole;

  public RunContentExecutor(@NotNull Project project, @NotNull ProcessHandler process) {
    myProject = project;
    myProcess = process;
  }

  public RunContentExecutor withFilter(Filter filter) {
    myFilterList.add(filter);
    return this;
  }

  public RunContentExecutor withTitle(@NlsContexts.TabTitle String title) {
    myTitle = title;
    return this;
  }

  public RunContentExecutor withRerun(Runnable rerun) {
    myRerunAction = rerun;
    return this;
  }

  public RunContentExecutor withStop(@NotNull Runnable stop, @NotNull Computable<Boolean> stopEnabled) {
    myStopAction = stop;
    myStopEnabled = stopEnabled;
    return this;
  }

  public RunContentExecutor withAfterCompletion(Runnable afterCompletion) {
    myAfterCompletion = afterCompletion;
    return this;
  }

  public RunContentExecutor withHelpId(String helpId) {
    myHelpId = helpId;
    return this;
  }

  public RunContentExecutor withActivateToolWindow(boolean activateToolWindow) {
    myActivateToolWindow = activateToolWindow;
    return this;
  }

  public RunContentExecutor withFocusToolWindow(boolean focusToolWindow) {
    myFocusToolWindow = focusToolWindow;
    return this;
  }

  private ConsoleView createConsole() {
    TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(myProject);
    consoleBuilder.filters(myFilterList);
    final ConsoleView console = consoleBuilder.getConsole();

    if (myHelpId != null) {
      console.setHelpId(myHelpId);
    }
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    DefaultActionGroup actions = new DefaultActionGroup();

    final JComponent consolePanel = createConsolePanel(console, actions);
    RunContentDescriptor descriptor = new RunContentDescriptor(console, myProcess, consolePanel, myTitle);
    descriptor.setActivateToolWindowWhenAdded(myActivateToolWindow);
    descriptor.setAutoFocusContent(myFocusToolWindow);

    Disposer.register(descriptor, this);
    Disposer.register(descriptor, console);

    actions.add(new RerunAction(consolePanel));
    actions.add(new StopAction());
    actions.add(new CloseAction(executor, descriptor, myProject));

    RunContentManager.getInstance(myProject).showRunContent(executor, descriptor);
    return console;
  }

  public void run() {
    FileDocumentManager.getInstance().saveAllDocuments();

    // Use user-provided console if exist. Create new otherwise
    ConsoleView view = (myUserProvidedConsole != null ? myUserProvidedConsole :  createConsole());
    view.attachToProcess(myProcess);
    if (myAfterCompletion != null) {
      myProcess.addProcessListener(new ProcessListener() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          ApplicationManager.getApplication().invokeLater(myAfterCompletion);
        }
      });
    }
    myProcess.startNotify();
  }

  private static JComponent createConsolePanel(ConsoleView view, ActionGroup actions) {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(view.getComponent(), BorderLayout.CENTER);
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("RunContentExecutor", actions, false);
    actionToolbar.setTargetComponent(panel);
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
    return panel;
  }

  @Override
  public void dispose() {
  }

  /**
   * @param console console to use instead of new one. Pass null to always create new
   */
  public @NotNull RunContentExecutor withConsole(@Nullable ConsoleView console) {
    myUserProvidedConsole = console;
    return this;
  }

  private final class RerunAction extends AnAction {
    RerunAction(JComponent consolePanel) {
      super(CommonBundle.message("action.text.rerun"), CommonBundle.message("action.text.rerun"), AllIcons.Actions.Restart);
      registerCustomShortcutSet(CommonShortcuts.getRerun(), consolePanel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myRerunAction.run();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabledAndVisible(myRerunAction != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isDumbAware() {
      return true;
    }
  }

  private final class StopAction extends AnAction implements DumbAware {
  StopAction() {
    super(ExecutionBundle.messagePointer("action.AnAction.text.stop"),
          ExecutionBundle.messagePointer("action.AnAction.description.stop"), AllIcons.Actions.Suspend);
  }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myStopAction.run();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setVisible(myStopAction != null);
      e.getPresentation().setEnabled(myStopEnabled != null && myStopEnabled.compute());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }
}
