// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.BuildTargetScopeProvider;
import com.intellij.compiler.impl.CompileScopeUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class ArtifactBuildTargetScopeProvider extends BuildTargetScopeProvider {
  @Override
  public @NotNull List<TargetTypeBuildScope> getBuildTargetScopes(final @NotNull CompileScope baseScope,
                                                                  final @NotNull Project project, final boolean forceBuild) {
    final List<TargetTypeBuildScope> scopes = new ArrayList<>();
    ReadAction.run(() -> {
      final Set<Artifact> artifacts = ArtifactCompileScope.getArtifactsToBuild(project, baseScope, false);
      if (ArtifactCompileScope.getArtifacts(baseScope) == null) {
        Set<Module> modules = ArtifactUtil.getModulesIncludedInArtifacts(artifacts, project);
        CompileScopeUtil.addScopesForModules(modules, Collections.emptyList(), scopes, forceBuild);
      }
      if (!artifacts.isEmpty()) {
        boolean forceBuildForArtifacts = forceBuild || ArtifactCompileScope.isArtifactRebuildForced(baseScope);
        scopes.add(CompileScopeUtil.createScopeForArtifacts(artifacts, forceBuildForArtifacts));
      }
    });

    return scopes;
  }
}
