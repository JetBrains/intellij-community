package com.intellij.execution.runners;

import com.intellij.execution.console.ConsoleHistoryController;
import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.execution.process.ProcessHandler;
import org.jetbrains.annotations.NotNull;

public class LanguageConsoleBuilder {
  private LanguageConsoleImpl myConsole;
  private ProcessHandler myProcessHandler;

  public LanguageConsoleBuilder console(@NotNull LanguageConsoleView console) {
    myConsole = console.getConsole();
    return this;
  }

  public LanguageConsoleBuilder processHandler(ProcessHandler processHandler) {
    myProcessHandler = processHandler;
    return this;
  }

  public LanguageConsoleBuilder initActions(@NotNull BaseConsoleExecuteActionHandler executeActionHandler, @NotNull String historyType) {
    ConsoleExecuteAction action = myProcessHandler == null
                                  ? new ConsoleExecuteAction(myConsole, executeActionHandler)
                                  : new ProcessBackedConsoleExecuteAction(myConsole, myProcessHandler, executeActionHandler);
    action.registerCustomShortcutSet(action.getShortcutSet(), myConsole.getConsoleEditor().getComponent());

    new ConsoleHistoryController(historyType, "", myConsole, executeActionHandler.getConsoleHistoryModel()).install();
    return this;
  }
}