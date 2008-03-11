package com.intellij.execution.filters;

import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;

/**
 * @author dyoma
 */
public class TextConsoleBuilderImpl extends TextConsoleBuilder {
  private final Project myProject;
  private final ArrayList<Filter> myFilters = new ArrayList<Filter>();

  public TextConsoleBuilderImpl(final Project project) {
    myProject = project;
  }

  public ConsoleView getConsole() {
    final ConsoleViewImpl consoleView = new ConsoleViewImpl(myProject);
    for (final Filter filter : myFilters) {
      consoleView.addMessageFilter(filter);
    }
    return consoleView;
  }

  public void addFilter(final Filter filter) {
    myFilters.add(filter);
  }

}
