package com.intellij.execution;

import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a process and prints the output in a content tab within the Run toolwindow.
 *
 * @author yole
 */
public class RunContentExecutor {
  private final Project myProject;
  private final ProcessHandler myProcess;
  private final List<Filter> myFilterList = new ArrayList<Filter>();
  private Runnable myRerunAction;
  private Runnable myStopAction;
  private Runnable myAfterCompletion;
  private Computable<Boolean> myStopEnabled;
  private String myTitle = "Output";
  private String myHelpId = null;
  private boolean myActivateToolWindow = true;

  public RunContentExecutor(@NotNull Project project, @NotNull ProcessHandler process) {
    myProject = project;
    myProcess = process;
  }

  public RunContentExecutor withFilter(Filter filter) {
    myFilterList.add(filter);
    return this;
  }

  public RunContentExecutor withTitle(String title) {
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

  private ConsoleView createConsole(@NotNull Project project, @NotNull ProcessHandler processHandler) {
    TextConsoleBuilder consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    consoleBuilder.filters(myFilterList);
    ConsoleView console = consoleBuilder.getConsole();
    console.attachToProcess(processHandler);
    return console;
  }

  public void run() {
    FileDocumentManager.getInstance().saveAllDocuments();

    ConsoleView view = createConsole(myProject, myProcess);
    if (myHelpId != null) {
      view.setHelpId(myHelpId);
    }
    Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    DefaultActionGroup actions = new DefaultActionGroup();

    final JComponent consolePanel = createConsolePanel(view, actions);
    RunContentDescriptor descriptor = new RunContentDescriptor(view, myProcess, consolePanel, myTitle);

    actions.add(new RerunAction(consolePanel));
    actions.add(new StopAction());
    actions.add(new CloseAction(executor, descriptor, myProject));

    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(executor, descriptor);

    if (myActivateToolWindow) {
      activateToolWindow();
    }

    if (myAfterCompletion != null) {
      myProcess.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(ProcessEvent event) {
          SwingUtilities.invokeLater(myAfterCompletion);
        }
      });
    }

    myProcess.startNotify();
  }

  public void activateToolWindow() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.RUN).activate(null);
      }
    });
  }

  private static JComponent createConsolePanel(ConsoleView view, ActionGroup actions) {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(view.getComponent(), BorderLayout.CENTER);
    panel.add(createToolbar(actions), BorderLayout.WEST);
    return panel;
  }

  private static JComponent createToolbar(ActionGroup actions) {
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, false);
    return actionToolbar.getComponent();
  }

  private class RerunAction extends AnAction implements DumbAware {
    public RerunAction(JComponent consolePanel) {
      super("Rerun", "Rerun",
            AllIcons.Actions.Restart);
      registerCustomShortcutSet(CommonShortcuts.getRerun(), consolePanel);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myRerunAction.run();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setVisible(myRerunAction != null);
    }
  }

  private class StopAction extends AnAction implements DumbAware {
    public StopAction() {
      super("Stop", "Stop",
            AllIcons.Actions.Suspend);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myStopAction.run();
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setVisible(myStopAction != null);
      e.getPresentation().setEnabled(myStopEnabled != null && myStopEnabled.compute());
    }
  }
}
