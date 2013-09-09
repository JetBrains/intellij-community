package com.intellij.execution.runners;

import com.intellij.execution.console.LanguageConsoleImpl;
import com.intellij.execution.process.ProcessHandler;

class ProcessBackedConsoleExecuteAction extends ConsoleExecuteAction {
  private final ProcessHandler myProcessHandler;

  public ProcessBackedConsoleExecuteAction(LanguageConsoleImpl languageConsole,
                                           ProcessHandler processHandler,
                                           BaseConsoleExecuteActionHandler consoleExecuteActionHandler) {
    super(languageConsole, consoleExecuteActionHandler);

    myProcessHandler = processHandler;
  }

  @Override
  protected boolean isEnabled() {
    return !myProcessHandler.isProcessTerminated();
  }
}