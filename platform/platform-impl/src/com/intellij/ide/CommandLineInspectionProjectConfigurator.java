// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * @author yole
 */
public interface CommandLineInspectionProjectConfigurator {
  ExtensionPointName<CommandLineInspectionProjectConfigurator> EP_NAME = ExtensionPointName.create("com.intellij.commandLineInspectionProjectConfigurator");

  /**
   * Returns true if any additional configuration is required to inspect the project at the given path.
   */
  boolean isApplicable(@NotNull Path projectPath, @NotNull CommandLineInspectionProgressReporter logger);

  /**
   * Invoked before a project is imported.
   */
  default void configureEnvironment(@NotNull Path projectPath, @NotNull CommandLineInspectionProgressReporter logger) {
  }

  /**
   * This method is for {@link #isApplicable(Path, CommandLineInspectionProgressReporter)} inspections
   * after project is opened. In addition to the method, you may implement the
   * {@link #configureProject(Project, AnalysisScope, CommandLineInspectionProgressReporter)}
   * that is executed to prepare inspections run.
   *
   * @see #configureProject(Project, AnalysisScope, CommandLineInspectionProgressReporter)
   */
  default void configureProject(@NotNull Project project, @NotNull CommandLineInspectionProgressReporter logger) {

  }

  /**
   * Invoked after the project has been imported and before the analysis on the specified scope
   * is started.
   *
   * @see #configureProject(Project, CommandLineInspectionProgressReporter)
   */
  default void configureProject(@NotNull Project project, @NotNull AnalysisScope scope, @NotNull CommandLineInspectionProgressReporter logger) {
    configureProject(project, logger);
  }
}
