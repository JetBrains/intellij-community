// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl;

import com.intellij.compiler.ModuleSourceSet;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.api.CmdlineProtoUtil;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.builders.java.ResourcesTargetType;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType;

import java.util.*;
import java.util.function.Function;

public final class CompileScopeUtil {
  private static final Key<List<TargetTypeBuildScope>> BASE_SCOPE_FOR_EXTERNAL_BUILD = Key.create("SCOPE_FOR_EXTERNAL_BUILD");

  public static void setBaseScopeForExternalBuild(@NotNull CompileScope scope, @NotNull List<TargetTypeBuildScope> scopes) {
    scope.putUserData(BASE_SCOPE_FOR_EXTERNAL_BUILD, scopes);
  }

  public static void setResourcesScopeForExternalBuild(@NotNull CompileScope scope, @NotNull List<String> moduleNames) {
    List<TargetTypeBuildScope> resourceScopes = new ArrayList<>();
    for (UpdateResourcesBuildContributor provider : UpdateResourcesBuildContributor.EP_NAME.getExtensions()) {
      for (BuildTargetType<?> type : provider.getResourceTargetTypes()) {
        resourceScopes.add(CmdlineProtoUtil.createTargetsScope(type.getTypeId(), moduleNames, false));
      }
    }
    setBaseScopeForExternalBuild(scope, resourceScopes);
  }

  public static void addScopesForModules(Collection<? extends Module> modules,
                                         Collection<String> unloadedModules,
                                         List<? super TargetTypeBuildScope> scopes,
                                         boolean forceBuild) {
    if (!modules.isEmpty() || !unloadedModules.isEmpty()) {
      for (JavaModuleBuildTargetType type : JavaModuleBuildTargetType.ALL_TYPES) {
        TargetTypeBuildScope.Builder builder = TargetTypeBuildScope.newBuilder().setTypeId(type.getTypeId()).setForceBuild(forceBuild);
        for (Module module : modules) {
          builder.addTargetId(module.getName());
        }
        for (String unloadedModule : unloadedModules) {
          builder.addTargetId(unloadedModule);
        }
        scopes.add(builder.build());
      }
    }
  }

  public static void addScopesForSourceSets(Collection<? extends ModuleSourceSet> sets, Collection<String> unloadedModules, List<? super TargetTypeBuildScope> scopes, boolean forceBuild) {
    if (sets.isEmpty() && unloadedModules.isEmpty()) {
      return;
    }
    final Map<BuildTargetType<?>, Set<String>> targetsByType = new HashMap<>();
    final Function<BuildTargetType<?>, Set<String>> idsOf = targetType -> {
      Set<String> ids = targetsByType.get(targetType);
      if (ids == null) {
        ids = new HashSet<>();
        targetsByType.put(targetType, ids);
      }
      return ids;
    };
    for (ModuleSourceSet set : sets) {
      final BuildTargetType<?> targetType = toTargetType(set);
      assert targetType != null;
      idsOf.apply(targetType).add(set.getModule().getName());
    }
    if (!unloadedModules.isEmpty()) {
      for (JavaModuleBuildTargetType targetType : JavaModuleBuildTargetType.ALL_TYPES) {
        idsOf.apply(targetType).addAll(unloadedModules);
      }
    }

    for (Map.Entry<BuildTargetType<?>, Set<String>> entry : targetsByType.entrySet()) {
      TargetTypeBuildScope.Builder builder = TargetTypeBuildScope.newBuilder().setTypeId(entry.getKey().getTypeId()).setForceBuild(forceBuild);
      for (String targetId : entry.getValue()) {
        builder.addTargetId(targetId);
      }
      scopes.add(builder.build());
    }
  }

  private static BuildTargetType<?> toTargetType(ModuleSourceSet set) {
    return switch (set.getType()) {
      case TEST -> JavaModuleBuildTargetType.TEST;
      case PRODUCTION -> JavaModuleBuildTargetType.PRODUCTION;
      case RESOURCES -> ResourcesTargetType.PRODUCTION;
      case RESOURCES_TEST -> ResourcesTargetType.TEST;
    };
  }

  public static List<TargetTypeBuildScope> getBaseScopeForExternalBuild(@NotNull CompileScope scope) {
    return scope.getUserData(BASE_SCOPE_FOR_EXTERNAL_BUILD);
  }

  public static List<TargetTypeBuildScope> mergeScopes(@NotNull List<TargetTypeBuildScope> scopes1,
                                                       @NotNull List<TargetTypeBuildScope> scopes2) {
    if (scopes2.isEmpty()) {
      return scopes1;
    }
    if (scopes1.isEmpty()) {
      return scopes2;
    }

    Map<String, TargetTypeBuildScope> scopeById = new HashMap<>();
    mergeScopes(scopeById, scopes1);
    mergeScopes(scopeById, scopes2);
    return new ArrayList<>(scopeById.values());
  }

  private static void mergeScopes(Map<String, TargetTypeBuildScope> scopeById, List<TargetTypeBuildScope> scopes) {
    for (TargetTypeBuildScope scope : scopes) {
      String id = scope.getTypeId();
      TargetTypeBuildScope old = scopeById.get(id);
      if (old == null) {
        scopeById.put(id, scope);
      }
      else {
        scopeById.put(id, mergeScope(old, scope));
      }
    }
  }

  private static TargetTypeBuildScope mergeScope(TargetTypeBuildScope scope1, TargetTypeBuildScope scope2) {
    String typeId = scope1.getTypeId();
    if (scope1.getAllTargets()) {
      return !scope1.getForceBuild() && scope2.getForceBuild() ? createAllTargetForcedBuildScope(typeId) : scope1;
    }
    if (scope2.getAllTargets()) {
      return !scope2.getForceBuild() && scope1.getForceBuild() ? createAllTargetForcedBuildScope(typeId) : scope2;
    }
    return TargetTypeBuildScope.newBuilder()
      .setTypeId(typeId)
      .setForceBuild(scope1.getForceBuild() || scope2.getForceBuild())
      .addAllTargetId(scope1.getTargetIdList())
      .addAllTargetId(scope2.getTargetIdList())
      .build();
  }

  private static TargetTypeBuildScope createAllTargetForcedBuildScope(final String typeId) {
    return TargetTypeBuildScope.newBuilder().setTypeId(typeId).setForceBuild(true).setAllTargets(true).build();
  }

  public static boolean allProjectModulesAffected(CompileContextImpl compileContext) {
    @SuppressWarnings("SSBasedInspection")
    Set<Module> allModules = new ObjectOpenHashSet<>(compileContext.getProjectCompileScope().getAffectedModules());
    for (Module module : compileContext.getCompileScope().getAffectedModules()) {
      allModules.remove(module);
    }
    return allModules.isEmpty();
  }

  public static List<String> fetchFiles(CompileContextImpl context) {
    if (context.isRebuild()) {
      return Collections.emptyList();
    }
    final CompileScope scope = context.getCompileScope();
    if (shouldFetchFiles(scope)) {
      return ContainerUtil.map(scope.getFiles(null, true), VirtualFile::getPath);
    }
    return Collections.emptyList();
  }

  private static boolean shouldFetchFiles(CompileScope scope) {
    if (scope instanceof CompositeScope) {
      for (CompileScope compileScope : ((CompositeScope)scope).getScopes()) {
        if (shouldFetchFiles(compileScope)) {
          return true;
        }
      }
    }
    return scope instanceof OneProjectItemCompileScope || scope instanceof FileSetCompileScope;
  }

  public static TargetTypeBuildScope createScopeForArtifacts(Collection<? extends Artifact> artifacts,
                                                             boolean forceBuild) {
    TargetTypeBuildScope.Builder builder = TargetTypeBuildScope.newBuilder()
                                                               .setTypeId(ArtifactBuildTargetType.INSTANCE.getTypeId())
                                                               .setForceBuild(
                                                                 forceBuild);
    for (Artifact artifact : artifacts) {
      builder.addTargetId(artifact.getName());
    }
    return builder.build();
  }
}
