package com.intellij.execution.runners;

import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import org.jetbrains.annotations.NotNull;

public class LanguageConsoleBuilder {
  private LanguageConsoleImpl myConsole;
  private Condition<LanguageConsoleImpl> myExecutionEnabled = Conditions.alwaysTrue();

  public LanguageConsoleBuilder console(@NotNull LanguageConsoleView console) {
    myConsole = console.getConsole();
    return this;
  }

  public LanguageConsoleBuilder processHandler(@NotNull ProcessHandler processHandler) {
    myExecutionEnabled = new ProcessBackedExecutionEnabledCondition(processHandler);
    return this;
  }

  public LanguageConsoleBuilder executionEnabled(@NotNull Condition<LanguageConsoleImpl> condition) {
    myExecutionEnabled = condition;
    return this;
  }

  public LanguageConsoleBuilder initActions(@NotNull BaseConsoleExecuteActionHandler executeActionHandler, @NotNull String historyType) {
    ConsoleExecuteAction action = new ConsoleExecuteAction(myConsole, executeActionHandler, ConsoleExecuteAction.CONSOLE_EXECUTE_ACTION_ID, myExecutionEnabled);
    action.registerCustomShortcutSet(action.getShortcutSet(), myConsole.getConsoleEditor().getComponent());

    new ConsoleHistoryController(historyType, "", myConsole, executeActionHandler.getConsoleHistoryModel()).install();
    return this;
  }

  static class ProcessBackedExecutionEnabledCondition implements Condition<LanguageConsoleImpl> {
    private ProcessHandler myProcessHandler;

    public ProcessBackedExecutionEnabledCondition(ProcessHandler myProcessHandler) {
      this.myProcessHandler = myProcessHandler;
    }

    @Override
    public boolean value(LanguageConsoleImpl console) {
      return !myProcessHandler.isProcessTerminated();
    }
  }
}