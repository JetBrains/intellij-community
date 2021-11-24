// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.analysis.AnalysisScope;
import com.intellij.ide.impl.PatchProjectUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Extension point that helps prepare project for opening in headless or automated environments.
 * Implementation must be stateless.
 */
public interface CommandLineInspectionProjectConfigurator {
  ExtensionPointName<CommandLineInspectionProjectConfigurator> EP_NAME = ExtensionPointName.create("com.intellij.commandLineInspectionProjectConfigurator");

  interface ConfiguratorContext {
    @NotNull CommandLineInspectionProgressReporter getLogger();

    /**
     * progress indicator can be used in the actions to report updates or check for cancellation
     */
    @NotNull ProgressIndicator getProgressIndicator();

    /**
     * Project that is about to be open
     */
    @NotNull Path getProjectPath();

    /**
     * Use this filter in the implementation to avoid configuring parts of the project
     * that are not intended to be (e.g. testData). It is up to user to provide filters.
     *
     * @see PatchProjectUtil#patchProject(Project)
     */
    @NotNull Predicate<Path> getFilesFilter();

    /**
     * The same predicate as {@link #getFilesFilter()} but for {@link VirtualFile}
     */
    default @NotNull Predicate<VirtualFile> getVirtualFilesFilter() {
      Predicate<Path> filesPredicate = getFilesFilter();
      return file -> {
        Path path = file.getFileSystem().getNioPath(file);
        return path != null && filesPredicate.test(path);
      };
    }

    /**
     * Allows to grab additional information from the context about the current inspections running,
     * if available
     */
    @Nullable
    default AnalysisScope getAnalyzerScope() {
      return null;
    }
  }

  /**
   * @return stable identifier that can be used to disable a configurator from user scripts
   */
  @NotNull
  String getName();

  /**
   * @return human readable description on the configurator actions
   */
  @NotNull
  @Nls(capitalization = Nls.Capitalization.Sentence)
  String getDescription();

  /**
   * @returns true if any additional configuration is required to inspect the project at the given path.
   */
  default boolean isApplicable(@NotNull ConfiguratorContext context) {
    return true;
  }

  /**
   * Invoked before a project is imported on the project directory. Only for {@link #isApplicable(ConfiguratorContext)}
   * extensions
   */
  default void configureEnvironment(@NotNull ConfiguratorContext context) {
  }

  /**
   * This method is for {@link #isApplicable(ConfiguratorContext)} extensions
   * after project is opened.
   */
  default void preConfigureProject(@NotNull Project project, @NotNull ConfiguratorContext context) {

  }
  /**
   * This method is for {@link #isApplicable(ConfiguratorContext)} extensions
   * after project is opened.
   */
  default void configureProject(@NotNull Project project,
                                @NotNull ConfiguratorContext context) {

  }
}
