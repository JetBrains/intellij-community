package com.intellij.diagnostic.logging;

import com.intellij.execution.configurations.AdditionalTabComponentManager;

/**
 * User: anna
 * Date: 01-Feb-2006
 */
public interface LogConsoleManager extends AdditionalTabComponentManager {
  void addLogConsole(final String name, final String path, final long skippedContent);
  void removeLogConsole(final String path);
}
