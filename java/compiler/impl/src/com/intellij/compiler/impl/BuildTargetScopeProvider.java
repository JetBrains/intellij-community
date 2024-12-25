// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerFilter;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

/**
 * Allows to control the list of build targets which are compiled when the Make action is invoked for a specific scope.
 */
public abstract class BuildTargetScopeProvider {
  public static final ExtensionPointName<BuildTargetScopeProvider> EP_NAME = ExtensionPointName.create("com.intellij.compiler.buildTargetScopeProvider");

  /**
   * @deprecated override {@link #getBuildTargetScopes(CompileScope, Project, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Contract(pure = true)
  public @NotNull List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope, @NotNull CompilerFilter filter,
                                                         @NotNull Project project, boolean forceBuild) {
    return Collections.emptyList();
  }

  @Contract(pure = true)
  public @NotNull List<TargetTypeBuildScope> getBuildTargetScopes(@NotNull CompileScope baseScope, @NotNull Project project, boolean forceBuild) {
    return getBuildTargetScopes(baseScope, CompilerFilter.ALL, project, forceBuild);
  }
}
