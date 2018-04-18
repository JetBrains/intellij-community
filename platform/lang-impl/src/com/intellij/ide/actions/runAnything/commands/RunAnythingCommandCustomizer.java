// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.commands;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * This class customizes 'Run Anything' command line and its data context.
 * E.g. it's possible to wrap command into a shell or/and patch environment variables.
 */
public abstract class RunAnythingCommandCustomizer {
  public static final ExtensionPointName<RunAnythingCommandCustomizer> EP_NAME =
    ExtensionPointName.create("com.intellij.runAnything.commandCustomizer");

  /**
   * Customizes command line and pass it to others customizers
   *
   * @param workDirectory the working directory the command will be executed in
   * @param dataContext   {@link DataContext} to fetch module, project etc.
   * @param commandLine   command line to be executed
   * @return customized command line
   */
  @NotNull
  public GeneralCommandLine customizeCommandLine(@NotNull VirtualFile workDirectory,
                                                 @NotNull DataContext dataContext,
                                                 @NotNull GeneralCommandLine commandLine) {
    return commandLine;
  }

  /**
   * Customizes data context and pass it to others providers
   *
   * @param dataContext {@link DataContext} to fetch module, project etc.
   * @return customized {@link DataContext}
   */
  @NotNull
  public DataContext customizeDataContext(@NotNull DataContext dataContext) {
    return dataContext;
  }

  @NotNull
  public static GeneralCommandLine customizeCommandLine(@NotNull DataContext dataContext,
                                                        @NotNull VirtualFile workDirectory,
                                                        @NotNull String command) {
    GeneralCommandLine commandLine = new GeneralCommandLine(command)
      .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);

    for (RunAnythingCommandCustomizer customizer : EP_NAME.getExtensions()) {
      commandLine = customizer.customizeCommandLine(workDirectory, dataContext, commandLine);
    }

    return commandLine;
  }

  @NotNull
  public static DataContext customizeContext(@NotNull DataContext dataContext) {
    for (RunAnythingCommandCustomizer customizer : EP_NAME.getExtensions()) {
      dataContext = customizer.customizeDataContext(dataContext);
    }

    return dataContext;
  }
}