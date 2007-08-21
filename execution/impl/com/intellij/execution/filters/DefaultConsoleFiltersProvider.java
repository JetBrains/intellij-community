/*
 * User: anna
 * Date: 20-Aug-2007
 */
package com.intellij.execution.filters;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class DefaultConsoleFiltersProvider implements ConsoleFilterProvider{
  public Filter[] getDefaultFilters(@NotNull Project project) {
    return new Filter[]{new ExceptionFilter(project), new YourkitFilter(project)};
  }
}