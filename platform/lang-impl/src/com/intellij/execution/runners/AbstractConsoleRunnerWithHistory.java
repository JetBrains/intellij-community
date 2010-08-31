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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * @author oleg
 * This class provides basic functionality for running consoles.
 * It launches extrnal process and handles line input with history
 */
public abstract class AbstractConsoleRunnerWithHistory {
  protected final Project myProject;
  protected final String myConsoleTitle;

  protected OSProcessHandler myProcessHandler;
  protected final CommandLineArgumentsProvider myProvider;
  protected final String myWorkingDir;

  protected LanguageConsoleViewImpl myConsoleView;
  private final ConsoleHistoryModel myHistory = new ConsoleHistoryModel();
  private AnAction myRunAction;

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
   * @throws ExecutionException
   */
  public void initAndRun() throws ExecutionException {
    // Create Server process
    final Process process = createProcess();

    // Init console view
    myConsoleView = createConsoleView();

    myProcessHandler = createProcessHandler(process);

    ProcessTerminatedListener.attach(myProcessHandler);

    myProcessHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        myRunAction.getTemplatePresentation().setEnabled(false);
        myConsoleView.getConsole().setPrompt("");
        myConsoleView.getConsole().getConsoleEditor().setRendererMode(true);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            myConsoleView.getConsole().getConsoleEditor().getComponent().updateUI();
          }
        });
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

  protected abstract LanguageConsoleViewImpl createConsoleView();

  @Nullable
  protected abstract Process createProcess() throws ExecutionException;

  protected abstract OSProcessHandler createProcessHandler(final Process process);

  private void registerActionShortcuts(final AnAction[] actions, final JComponent component) {
    for (AnAction action : actions) {
      if (action.getShortcutSet() != null) {
        action.registerCustomShortcutSet(action.getShortcutSet(), component);
      }
    }
  }

  protected AnAction[] fillToolBarActions(final DefaultActionGroup toolbarActions,
                                          final Executor defaultExecutor,
                                          final RunContentDescriptor myDescriptor) {
//stop
    final AnAction stopAction = createStopAction();
    toolbarActions.add(stopAction);

//close
    final AnAction closeAction = createCloseAction(defaultExecutor, myDescriptor);
    toolbarActions.add(closeAction);

// run action
    myRunAction = new DumbAwareAction(null, null, IconLoader.getIcon("/actions/execute.png")) {
      public void actionPerformed(final AnActionEvent e) {
        runExecuteActionInner(e);
      }

      public void update(final AnActionEvent e) {
        final EditorEx editor = getLanguageConsole().getConsoleEditor();
        final Lookup lookup = LookupManager.getActiveLookup(editor);
        e.getPresentation().setEnabled(!myProcessHandler.isProcessTerminated() &&
                                       (lookup == null || !lookup.isCompletion()));
      }
    };
    EmptyAction.setupAction(myRunAction, "Console.Execute", null);
    toolbarActions.add(myRunAction);

// Help
    toolbarActions.add(CommonActionsManager.getInstance().createHelpAction("interactive_console"));

// history actions
    final PairProcessor<AnActionEvent, String> historyProcessor = new PairProcessor<AnActionEvent, String>() {
      public boolean process(final AnActionEvent e, final String s) {
        new WriteCommandAction(myProject, getLanguageConsole().getFile()) {
          protected void run(final Result result) throws Throwable {
            getLanguageConsole().getEditorDocument().setText(s == null? "" : s);
          }
        }.execute();
        return true;
      }
    };
    final AnAction historyNextAction = ConsoleHistoryModel.createHistoryAction(myHistory, true, historyProcessor);
    final AnAction historyPrevAction = ConsoleHistoryModel.createHistoryAction(myHistory, false, historyProcessor);
    historyNextAction.getTemplatePresentation().setVisible(false);
    historyPrevAction.getTemplatePresentation().setVisible(false);
    toolbarActions.add(historyNextAction);
    toolbarActions.add(historyPrevAction);

    return new AnAction[]{stopAction, closeAction, myRunAction, historyNextAction, historyPrevAction};
  }

  protected AnAction createCloseAction(final Executor defaultExecutor, final RunContentDescriptor myDescriptor) {
    return new CloseAction(defaultExecutor, myDescriptor, myProject);
  }

  protected AnAction createStopAction() {
    return ActionManager.getInstance().getAction(IdeActions.ACTION_STOP_PROGRAM);
  }

  public void processInput(final String input) {
    final Charset charset = myProcessHandler.getCharset();
    final OutputStream outputStream = myProcessHandler.getProcessInput();
    try {
      byte[] bytes = (input + "\n").getBytes(charset.name());
      outputStream.write(bytes);
      outputStream.flush();
    }
    catch (IOException e) {
      // ignore
    }
  }

  public LanguageConsoleImpl getLanguageConsole() {
    return myConsoleView.getConsole();
  }

  protected void runExecuteActionInner(final AnActionEvent actionEvent) {
    // Process input and add to history
    final Document document = getLanguageConsole().getCurrentEditor().getDocument();
    final String text = document.getText();
    final TextRange range = new TextRange(0, document.getTextLength());
    getLanguageConsole().getCurrentEditor().getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    getLanguageConsole().addCurrentToHistory(range, false);
    getLanguageConsole().setInputText("");
    if (!StringUtil.isEmptyOrSpaces(text)){
      myHistory.addToHistory(text);
    }
    // Send to interpreter / server
    processInput(text);
  }

  protected static String getProviderCommandLine(final CommandLineArgumentsProvider provider) {
    final StringBuilder builder = new StringBuilder();
    for (String s : provider.getArguments()) {
      if (builder.length() > 0){
        builder.append(' ');
      }
      builder.append(s);
    }
    return builder.toString();
  }

  public Project getProject() {
    return myProject;
  }
}
