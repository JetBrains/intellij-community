package com.intellij.execution.runners;

import com.intellij.execution.console.LanguageConsoleView;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class ConsoleRunnerWithHistory<T extends LanguageConsoleView> {
  protected final Project myProject;

  public ConsoleRunnerWithHistory(@NotNull Project project) {
    myProject = project;
  }
}