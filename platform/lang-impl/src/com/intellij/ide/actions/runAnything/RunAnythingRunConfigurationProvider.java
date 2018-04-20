// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this class if a particular run configuration should be created for matching input string.
 */
public abstract class RunAnythingRunConfigurationProvider {
  public static final ExtensionPointName<RunAnythingRunConfigurationProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.runAnything.runConfigurationProvider");

  /**
   * If {@code commandLine} is matched than current provider associated run configuration of factory {@link #getConfigurationFactory()}
   * will be created as {@link #createConfiguration(Project, String, VirtualFile)}.
   * <p>
   * E.g. for input string `ruby test.rb` a new temporary 'ruby script' run configuration will be created.
   *
   * @param commandLine   'Run Anything' input string
   * @param workDirectory command execution context directory
   * @return true if current provider associated run configuration should be created by 'commandLine'
   */
  public abstract boolean isMatched(@NotNull Project project, @NotNull String commandLine, @NotNull VirtualFile workDirectory);

  /**
   * Actual run configuration creation by {@code commandLine}
   *
   * @param commandLine      'Run Anything' input string
   * @param workingDirectory command execution context directory
   * @return created run configuration
   */
  @NotNull
  public abstract RunnerAndConfigurationSettings createConfiguration(@NotNull Project project,
                                                                     @NotNull String commandLine,
                                                                     @NotNull VirtualFile workingDirectory);

  /**
   * Returns current provider associated run configuration factory
   */
  @NotNull
  public abstract ConfigurationFactory getConfigurationFactory();

  /**
   * Finds matched provider along with all {@link RunAnythingRunConfigurationProvider} providers
   *
   * @param commandLine      'Run Anything' input string
   * @param workingDirectory command execution context directory
   */
  @Nullable
  public static RunAnythingRunConfigurationProvider findMatchedProvider(@NotNull Project project,
                                                                        @NotNull String commandLine,
                                                                        @NotNull VirtualFile workingDirectory) {
    for (RunAnythingRunConfigurationProvider provider : EP_NAME.getExtensions()) {
      if (provider.isMatched(project, commandLine, workingDirectory)) {
        return provider;
      }
    }

    return null;
  }
}