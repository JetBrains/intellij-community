package com.intellij.execution.runners;

@Deprecated
/**
 * @deprecated to remove in IDEA 15
 */
public abstract class BaseConsoleExecuteActionHandler extends com.intellij.execution.console.BaseConsoleExecuteActionHandler {
  public BaseConsoleExecuteActionHandler(boolean preserveMarkup) {
    super(preserveMarkup);
  }
}