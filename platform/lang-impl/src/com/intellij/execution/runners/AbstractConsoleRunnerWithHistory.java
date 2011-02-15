/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleViewImpl;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction;
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author oleg
 * This class provides basic functionality for running consoles.
 * It launches external process and handles line input with history
 */
public abstract class AbstractConsoleRunnerWithHistory {
  private final Project myProject;
  private final String myConsoleTitle;

  private ProcessHandler myProcessHandler;
  private final CommandLineArgumentsProvider myProvider;
  private final String myWorkingDir;

  private LanguageConsoleViewImpl myConsoleView;

  private AnAction myRunAction;

  private ConsoleExecuteActionHandler myConsoleExecuteActionHandler;

  public AbstractConsoleRunnerWithHistory(@NotNull final Project project,
                                          @NotNull final String consoleTitle,
                                          @NotNull final CommandLineArgumentsProvider provider,
                                          @Nullable final String workingDir) {
    myProject = project;
    myConsoleTitle = consoleTitle;
    myProvider = provider;
    myWorkingDir = workingDir;
  }

  /**
   * Launch process, setup history, actions etc.
   *
   * @throws ExecutionException
   */
  public void initAndRun() throws ExecutionException {
    // Create Server process
    final Process process = createProcess(myProvider);

    // Init console view
    myConsoleView = createConsoleView();

    myProcessHandler = createProcessHandler(process, myProvider.getCommandLineString());

    myConsoleExecuteActionHandler = createConsoleExecuteActionHandler();

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
    final Executor defaultExecutor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false);

// Runner creating
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);
    panel.add(myConsoleView.getComponent(), BorderLayout.CENTER);

    final RunContentDescriptor myDescriptor =
      new RunContentDescriptor(myConsoleView, myProcessHandler, panel, myConsoleTitle);

// tool bar actions
    final AnAction[] actions = fillToolBarActions(toolbarActions, defaultExecutor, myDescriptor);
    registerActionShortcuts(actions, getLanguageConsole().getConsoleEditor().getComponent());
    registerActionShortcuts(actions, panel);
    panel.updateUI();

// Show in run toolwindow
    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(defaultExecutor, myDescriptor);

// Request focus
    final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(defaultExecutor.getId());
    window.activate(new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(myProject).requestFocus(getLanguageConsole().getCurrentEditor().getContentComponent(), true);
      }
    });
// Run
    myProcessHandler.startNotify();
  }

  protected void finishConsole() {
    myRunAction.getTemplatePresentation().setEnabled(false);
    myConsoleView.getConsole().setPrompt("");
    myConsoleView.getConsole().getConsoleEditor().setRendererMode(true);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myConsoleView.getConsole().getConsoleEditor().getComponent().updateUI();
      }
    });
  }

  protected abstract LanguageConsoleViewImpl createConsoleView();

  @Nullable
  protected abstract Process createProcess(CommandLineArgumentsProvider provider) throws ExecutionException;

  protected abstract OSProcessHandler createProcessHandler(final Process process, final String commandLine);

  public static void registerActionShortcuts(final AnAction[] actions, final JComponent component) {
    for (AnAction action : actions) {
      if (action.getShortcutSet() != null) {
        action.registerCustomShortcutSet(action.getShortcutSet(), component);
      }
    }
  }

  protected AnAction[] fillToolBarActions(final DefaultActionGroup toolbarActions,
                                          final Executor defaultExecutor,
                                          final RunContentDescriptor myDescriptor) {

    List<AnAction> actionList = Lists.newArrayList();

//stop
    final AnAction stopAction = createStopAction();
    actionList.add(stopAction);

//close
    final AnAction closeAction = createCloseAction(defaultExecutor, myDescriptor);
    actionList.add(closeAction);

// run and history actions

    ConsoleExecutionActions executionActions =
      createConsoleExecActions(getLanguageConsole(), myProcessHandler, myConsoleExecuteActionHandler);
    myRunAction = executionActions.getRunAction();
    actionList.addAll(executionActions.getActionsAsList());

// Help
    actionList.add(CommonActionsManager.getInstance().createHelpAction("interactive_console"));

    AnAction[] actions = actionList.toArray(new AnAction[actionList.size()]);

    toolbarActions.addAll(actions);

    return actions;
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

  public static ConsoleExecutionActions createConsoleExecActions(final LanguageConsoleImpl languageConsole,
                                                                 final ProcessHandler processHandler,
                                                                 final ConsoleExecuteActionHandler consoleExecuteActionHandler) {
    final ConsoleHistoryModel myHistory = new ConsoleHistoryModel();

    final AnAction runAction = new ConsoleExecuteAction(languageConsole,
                                                        myHistory, processHandler, consoleExecuteActionHandler);

    final PairProcessor<AnActionEvent, String> historyProcessor = new PairProcessor<AnActionEvent, String>() {
      public boolean process(final AnActionEvent e, final String s) {
        new WriteCommandAction(languageConsole.getProject(), languageConsole.getFile()) {
          protected void run(final Result result) throws Throwable {
            languageConsole.getEditorDocument().setText(s == null ? "" : s);
          }
        }.execute();
        return true;
      }
    };

    final EditorEx consoleEditor = languageConsole.getConsoleEditor();
    final AnAction upAction = ConsoleHistoryModel.createConsoleHistoryUpAction(createCanMoveUpComputable(consoleEditor),
                                                                               myHistory,
                                                                               historyProcessor);
    final AnAction downAction = ConsoleHistoryModel.createConsoleHistoryDownAction(createCanMoveDownComputable(consoleEditor),
                                                                                   myHistory,
                                                                                   historyProcessor);

    return new ConsoleExecutionActions(runAction, downAction, upAction);
  }

  public static Computable<Boolean> createCanMoveDownComputable(final Editor consoleEditor) {
    return new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        final Document document = consoleEditor.getDocument();
        final CaretModel caretModel = consoleEditor.getCaretModel();

        // Check if we have active lookup or if we can move in editor
        return LookupManager.getActiveLookup(consoleEditor) != null ||
               document.getLineNumber(caretModel.getOffset()) < document.getLineCount() - 1 &&
                                !StringUtil.isEmptyOrSpaces(document.getText().substring(caretModel.getOffset()));
      }
    };
  }

  public static Computable<Boolean> createCanMoveUpComputable(final Editor consoleEditor) {
    return new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        final Document document = consoleEditor.getDocument();
        final CaretModel caretModel = consoleEditor.getCaretModel();
        // Check if we have active lookup or if we can move in editor
        return LookupManager.getActiveLookup(consoleEditor) != null || document.getLineNumber(caretModel.getOffset()) > 0;
      }
    };
  }

  @NotNull
  protected abstract ConsoleExecuteActionHandler createConsoleExecuteActionHandler();

  public static class ConsoleExecutionActions {
    private final AnAction myRunAction;
    private final AnAction myNextAction;
    private final AnAction myPrevAction;


    public ConsoleExecutionActions(AnAction runAction, AnAction nextAction, AnAction prevAction) {
      myRunAction = runAction;
      myNextAction = nextAction;
      myPrevAction = prevAction;
    }

    public AnAction[] getActions() {
      return getActionsAsList().toArray(new AnAction[getActionsAsList().size()]);
    }


    public List<AnAction> getActionsAsList() {
      return Lists.newArrayList(myRunAction, myNextAction, myPrevAction);
    }

    public AnAction getRunAction() {
      return myRunAction;
    }
  }


  public static class ConsoleExecuteAction extends DumbAwareAction {
    public static final String ACTIONS_EXECUTE_ICON = "/actions/execute.png";
    public static final String CONSOLE_EXECUTE = "Console.Execute";

    private final LanguageConsoleImpl myLanguageConsole;
    private final ConsoleHistoryModel myHistory;
    private final ProcessHandler myProcessHandler;

    private final ConsoleExecuteActionHandler myConsoleExecuteActionHandler;


    public ConsoleExecuteAction(LanguageConsoleImpl languageConsole,
                                ConsoleHistoryModel history,
                                ProcessHandler processHandler,
                                ConsoleExecuteActionHandler consoleExecuteActionHandler) {
      super(null, null, IconLoader.getIcon(ACTIONS_EXECUTE_ICON));
      myLanguageConsole = languageConsole;
      myHistory = history;
      myProcessHandler = processHandler;
      myConsoleExecuteActionHandler = consoleExecuteActionHandler;
      EmptyAction.setupAction(this, CONSOLE_EXECUTE, null);
    }

    public void actionPerformed(final AnActionEvent e) {
      myConsoleExecuteActionHandler.runExecuteAction(myLanguageConsole, myHistory);
    }

    public void update(final AnActionEvent e) {
      final EditorEx editor = myLanguageConsole.getConsoleEditor();
      final Lookup lookup = LookupManager.getActiveLookup(editor);
      e.getPresentation().setEnabled(!myProcessHandler.isProcessTerminated() &&
                                     (lookup == null || !lookup.isCompletion()));
    }
  }

  public LanguageConsoleViewImpl getConsoleView() {
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

  public ConsoleExecuteActionHandler getConsoleExecuteActionHandler() {
    return myConsoleExecuteActionHandler;
  }
}
