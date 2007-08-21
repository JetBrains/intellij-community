/*
 * User: anna
 * Date: 20-Aug-2007
 */
package com.intellij.execution.filters;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface ConsoleFilterProvider {
  ExtensionPointName<ConsoleFilterProvider> FILTER_PROVIDERS = ExtensionPointName.create("com.intellij.consoleFilterProvider");

  @NotNull
  Filter[] getDefaultFilters(@NotNull Project project);
}