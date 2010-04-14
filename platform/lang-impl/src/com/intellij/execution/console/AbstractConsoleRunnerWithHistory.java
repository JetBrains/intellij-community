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
package com.intellij.execution.console;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * @author oleg
 */
public class AbstractConsoleRunnerWithHistory {
/*  protected final Project myProject;
  protected final String myConsoleTitle;

  private OSProcessHandler myProcessHandler;
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

  public static void run(@NotNull final Project project,
                         @NotNull final String consoleTitle,
                         @NotNull final CommandLineArgumentsProvider provider,
                         @Nullable final String workingDir) {

    final AbstractConsoleRunnerWithHistory consoleRunner = new AbstractConsoleRunnerWithHistory(project, consoleTitle, provider, workingDir);
    try {
      consoleRunner.initAndRun();
    }
    catch (ExecutionException e) {
      ExecutionHelper.showErrors(project, Arrays.<Exception>asList(e), consoleTitle, null);
    }
  }

  public void initAndRun() throws ExecutionException {
    // Create Server process
    final Process process = createProcess();

    // Init console view
    myConsoleView = createConsoleView();

    myProcessHandler = createProcessHandler(process);

    ProcessTerminatedListener.attach(myProcessHandler);

    // Set language level
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      final Sdk pythonSdk = PythonSdkType.findPythonSdk(module);
      if (pythonSdk != null){
        final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(pythonSdk);
        final PsiFile psiFile = getLanguageConsole().getFile();
        // Set module explicitly
        psiFile.putUserData(ModuleUtil.KEY_MODULE, module);
        final VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile != null) {
          // Set language level
          vFile.putUserData(LanguageLevel.KEY, languageLevel);
        }
        break;
      }
    }

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

// Setup default prompt
    myConsoleView.getConsole().setPrompt(PyConsoleHighlightingUtil.ORDINARY_PROMPT.trim());

// Attach to process
    myConsoleView.attachToProcess(myProcessHandler);

// Add filter TODO[oleg]: Add stacktrace filters
//    myConsoleView.addMessageFilter(new OutputConsoleFilter());

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

  protected LanguageConsoleViewImpl createConsoleView() {
    return new PyLanguageConsoleView(myProject, myConsoleTitle);
  }


  @Nullable
  protected Process createProcess() throws ExecutionException {
    return Runner.createProcess(myWorkingDir, true, myProvider.getAdditionalEnvs(), myProvider.getArguments());
  }

  private PyConsoleProcessHandler createProcessHandler(final Process process) {
    final Charset outputEncoding = EncodingManager.getInstance().getDefaultCharset();
    return new PyConsoleProcessHandler(process, myConsoleView.getConsole(), getProviderCommandLine(myProvider), outputEncoding);
  }

  private void registerActionShortcuts(final AnAction[] actions, final JComponent component) {
    for (AnAction action : actions) {
      if (action.getShortcutSet() != null) {
        action.registerCustomShortcutSet(action.getShortcutSet(), component);
      }
    }
  }

  private AnAction[] fillToolBarActions(final DefaultActionGroup toolbarActions,
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
        runExecuteActionInner(true);
      }

      public void update(final AnActionEvent e) {
        final EditorEx editor = getLanguageConsole().getConsoleEditor();
        final Lookup lookup = LookupManager.getActiveLookup(editor);
        e.getPresentation().setEnabled(!myProcessHandler.isProcessTerminated() &&
                                       (lookup == null || !lookup.isCompletion()));
      }
    };
    EmptyAction.setupAction(myRunAction, "Console.Python.Execute", null);
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

  protected void sendInput(final String input) {
    final Charset charset = myProcessHandler.getCharset();
    final OutputStream outputStream = myProcessHandler.getProcessInput();
    try {
      byte[] bytes = input.getBytes(charset.name());
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

  private void runExecuteActionInner(final boolean erase) {
    // Process input and add to history
    final Document document = getLanguageConsole().getCurrentEditor().getDocument();
    final String documentText = document.getText();
    final TextRange range = new TextRange(0, document.getTextLength());
    getLanguageConsole().getCurrentEditor().getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
    getLanguageConsole().addCurrentToHistory(range, false);
    if (erase) {
      getLanguageConsole().setInputText("");
    }
    final String line = documentText;
    if (!StringUtil.isEmptyOrSpaces(line)){
      myHistory.addToHistory(line);
    }
    // Send to interpreter / server
    final String text2send = line.length() == 0 ? "\n\n" : line + "\n";
    sendInput(text2send);

    if (myConsoleView instanceof ConsoleNotification){
      ((ConsoleNotification)myConsoleView).inputSent(text2send);
    }
  }

  private static String getProviderCommandLine(final CommandLineArgumentsProvider provider) {
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
  }*/
}
