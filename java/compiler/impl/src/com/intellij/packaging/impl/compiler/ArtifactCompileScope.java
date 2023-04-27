// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.compiler;

import com.intellij.compiler.impl.ModuleCompileScope;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.elements.ArtifactElementType;
import com.intellij.packaging.impl.elements.ProductionModuleOutputElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ArtifactCompileScope {
  private static final Key<Boolean> FORCE_ARTIFACT_BUILD = Key.create("force_artifact_build");
  private static final Key<Artifact[]> ARTIFACTS_KEY = Key.create("artifacts");
  private static final Key<Set<Artifact>> CACHED_ARTIFACTS_KEY = Key.create("cached_artifacts");

  private ArtifactCompileScope() {
  }

  public static ModuleCompileScope createScopeForModulesInArtifacts(@NotNull Project project, @NotNull Collection<? extends Artifact> artifacts) {
    final Set<Module> modules = ArtifactUtil.getModulesIncludedInArtifacts(artifacts, project);
    return new ModuleCompileScope(project, modules.toArray(Module.EMPTY_ARRAY), true);
  }

  public static CompileScope createArtifactsScope(@NotNull Project project,
                                                  @NotNull Collection<? extends Artifact> artifacts) {
    return createArtifactsScope(project, artifacts, false);
  }

  public static CompileScope createArtifactsScope(@NotNull Project project,
                                                  @NotNull Collection<? extends Artifact> artifacts,
                                                  final boolean forceArtifactBuild) {
    return createScopeWithArtifacts(createScopeForModulesInArtifacts(project, artifacts), artifacts, forceArtifactBuild);
  }

  public static CompileScope createScopeWithArtifacts(final CompileScope baseScope,
                                                      @NotNull Collection<? extends Artifact> artifacts) {
    return createScopeWithArtifacts(baseScope, artifacts, false);
  }

  public static CompileScope createScopeWithArtifacts(final CompileScope baseScope, @NotNull Collection<? extends Artifact> artifacts, final boolean forceArtifactBuild) {
    baseScope.putUserData(ARTIFACTS_KEY, artifacts.toArray(new Artifact[0]));
    if (forceArtifactBuild) {
      baseScope.putUserData(FORCE_ARTIFACT_BUILD, Boolean.TRUE);
    }
    return baseScope;
  }

  public static Set<Artifact> getArtifactsToBuild(final Project project,
                                                  final CompileScope compileScope,
                                                  final boolean addIncludedArtifactsWithOutputPathsOnly) {
    final Artifact[] artifactsFromScope = getArtifacts(compileScope);
    final ArtifactManager artifactManager = ArtifactManager.getInstance(project);
    PackagingElementResolvingContext context = artifactManager.getResolvingContext();
    if (artifactsFromScope != null) {
      return addIncludedArtifacts(Arrays.asList(artifactsFromScope), context, addIncludedArtifactsWithOutputPathsOnly);
    }

    final Set<Artifact> cached = compileScope.getUserData(CACHED_ARTIFACTS_KEY);
    if (cached != null) {
      return cached;
    }

    Set<Artifact> artifacts = new HashSet<>();
    final Set<Module> modules = Set.of(compileScope.getAffectedModules());
    final List<Module> allModules = Arrays.asList(ModuleManager.getInstance(project).getModules());
    for (Artifact artifact : artifactManager.getArtifacts()) {
      if (artifact.isBuildOnMake()) {
        if (modules.containsAll(allModules)
            || containsModuleOutput(artifact, modules, context)) {
          artifacts.add(artifact);
        }
      }
    }
    Set<Artifact> result = addIncludedArtifacts(artifacts, context, addIncludedArtifactsWithOutputPathsOnly);
    compileScope.putUserData(CACHED_ARTIFACTS_KEY, result);
    return result;
  }

  public static Artifact @Nullable [] getArtifacts(CompileScope compileScope) {
    return compileScope.getUserData(ARTIFACTS_KEY);
  }

  public static boolean isArtifactRebuildForced(@NotNull CompileScope scope) {
    return Boolean.TRUE.equals(scope.getUserData(FORCE_ARTIFACT_BUILD));
  }

  private static boolean containsModuleOutput(Artifact artifact, final Set<? extends Module> modules, final PackagingElementResolvingContext context) {
    return !ArtifactUtil.processPackagingElements(artifact, ProductionModuleOutputElementType.ELEMENT_TYPE,
                                                  moduleOutputPackagingElement -> {
                                                    final Module module = moduleOutputPackagingElement.findModule(context);
                                                    return module == null || !modules.contains(module);
                                                  }, context, true);
  }

  @NotNull
  private static Set<Artifact> addIncludedArtifacts(@NotNull Collection<? extends Artifact> artifacts,
                                                    @NotNull PackagingElementResolvingContext context,
                                                    final boolean withOutputPathOnly) {
    Set<Artifact> result = new HashSet<>();
    for (Artifact artifact : artifacts) {
      collectIncludedArtifacts(artifact, context, new HashSet<>(), result, withOutputPathOnly);
    }
    return result;
  }

  private static void collectIncludedArtifacts(Artifact artifact, final PackagingElementResolvingContext context,
                                               final Set<? super Artifact> processed, final Set<? super Artifact> result, final boolean withOutputPathOnly) {
    if (!processed.add(artifact)) {
      return;
    }
    if (!withOutputPathOnly || !StringUtil.isEmpty(artifact.getOutputPath())) {
      result.add(artifact);
    }

    ArtifactUtil.processPackagingElements(artifact, ArtifactElementType.ARTIFACT_ELEMENT_TYPE, element -> {
      Artifact included = element.findArtifact(context);
      if (included != null) {
        collectIncludedArtifacts(included, context, processed, result, withOutputPathOnly);
      }
      return true;
    }, context, false);
  }
}
