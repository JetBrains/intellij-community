// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RunAnythingProvider {
  public static final ExtensionPointName<RunAnythingProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.runAnythingConfigurationProvider");

  public abstract boolean isMatched(Project project, @NotNull String commandLine, @NotNull VirtualFile workDirectory);

  @NotNull
  public abstract RunnerAndConfigurationSettings createConfiguration(@NotNull Project project,
                                                                     @NotNull String commandLine,
                                                                     @NotNull VirtualFile workingDirectory);

  @NotNull
  public abstract ConfigurationFactory getConfigurationFactory();

  @Nullable
  public static RunAnythingProvider findMatchedProvider(@NotNull Project project,
                                                        @NotNull String pattern,
                                                        @NotNull VirtualFile workDirectory) {
    for (RunAnythingProvider provider : EP_NAME.getExtensions()) {
      if (provider.isMatched(project, pattern, workDirectory)) {
        return provider;
      }
    }

    return null;
  }
}